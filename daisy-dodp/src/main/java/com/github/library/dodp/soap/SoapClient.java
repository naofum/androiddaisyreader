package com.github.library.dodp.soap;

import com.github.library.dodp.DodpNamespaces;
import com.github.library.dodp.client.DodpConfiguration;
import com.github.library.dodp.exception.DodpException;
import com.github.library.dodp.exception.DodpFaultException;
import com.github.library.dodp.exception.DodpTransportException;
import com.github.library.dodp.util.XmlUtil;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Low-level SOAP 1.1 transport for the DODP service. Handles envelope
 * construction, cookie persistence (session management) and SOAP fault
 * unmarshalling. All DODP operations are {@code document/literal} POST
 * requests with a {@code SOAPAction} header.
 *
 * <p>Implemented on OkHttp so it runs on Android (where {@code java.net.http}
 * is not available). Session cookies are retained via an in-memory
 * {@link CookieJar}.</p>
 */
public final class SoapClient implements AutoCloseable {

    private static final MediaType XML_MEDIA_TYPE = MediaType.get("text/xml; charset=UTF-8");

    /**
     * Populates the SOAP body element for a request.
     */
    public interface BodyWriter {
        void write(Element operation) throws DodpException;
    }

    /**
     * In-memory cookie jar retaining all cookies for the session, mirroring the
     * previous {@code CookiePolicy.ACCEPT_ALL} behaviour.
     */
    private static final class SessionCookieJar implements CookieJar {
        private final List<Cookie> store = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            // Replace cookies with the same name for the same host.
            synchronized (store) {
                for (Cookie incoming : cookies) {
                    store.removeIf(existing -> existing.name().equals(incoming.name())
                            && existing.domain().equals(incoming.domain()));
                    store.add(incoming);
                }
            }
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> matches = new ArrayList<>();
            synchronized (store) {
                for (Cookie cookie : store) {
                    if (cookie.matches(url)) {
                        matches.add(cookie);
                    }
                }
            }
            return matches;
        }

        void clear() {
            store.clear();
        }
    }

    private final DodpConfiguration config;
    private final SessionCookieJar cookieJar;
    private final OkHttpClient httpClient;

    public SoapClient(DodpConfiguration config) {
        this.config = config;
        this.cookieJar = new SessionCookieJar();
        this.httpClient = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(config.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                // Retries are handled explicitly in sendWithRetry.
                .retryOnConnectionFailure(false)
                .build();
    }

    public DodpConfiguration getConfiguration() {
        return config;
    }

    /**
     * Discards all session cookies (the server-side session token). Used by
     * {@link com.github.library.dodp.client.DodpSession#invalidate()} on
     * {@code logOff} and before re-authentication.
     */
    public void clearCookies() {
        cookieJar.clear();
    }

    /**
     * Invokes a DODP operation and returns the first element of the SOAP body
     * (the {@code <operation>Response} element).
     *
     * @param operation operation name, e.g. {@code logOn}; the SOAPAction is
     *                  derived as {@code "/" + operation}
     * @param writer    fills the operation element with its parameters
     */
    public Element invoke(String operation, BodyWriter writer) throws DodpException {
        Document envelope = XmlUtil.newDocument();
        Element soapEnvelope = envelope.createElementNS(DodpNamespaces.SOAP_ENVELOPE, "soap:Envelope");
        envelope.appendChild(soapEnvelope);
        Element soapBody = envelope.createElementNS(DodpNamespaces.SOAP_ENVELOPE, "soap:Body");
        soapEnvelope.appendChild(soapBody);
        Element operationElement = envelope.createElementNS(DodpNamespaces.DAISY_ONLINE, operation);
        soapBody.appendChild(operationElement);
        if (writer != null) {
            writer.write(operationElement);
        }

        String xml = XmlUtil.serialize(envelope);
        String soapAction = "\"/" + operation + "\"";

        HttpUrl url = HttpUrl.get(config.getEndpoint());
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("SOAPAction", soapAction)
                .header("Accept", "text/xml")
                .post(RequestBody.create(xml.getBytes(StandardCharsets.UTF_8), XML_MEDIA_TYPE));
        if (config.getUserAgent() != null) {
            requestBuilder.header("User-Agent", config.getUserAgent());
        }
        Request request = requestBuilder.build();

        return sendWithRetry(operation, request);
    }

    private Element sendWithRetry(String operation, Request request) throws DodpException {
        IOException lastException = null;
        int attempts = config.getMaxRetries() + 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                return parseResponse(operation, response);
            } catch (IOException e) {
                lastException = e;
                if (attempt < attempts - 1) {
                    sleepBeforeRetry(attempt);
                }
            }
        }
        throw new DodpTransportException("Network error while calling " + operation, lastException);
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            long millis = config.getRetryBackoff().toMillis();
            Thread.sleep(millis * (attempt + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Element parseResponse(String operation, Response response) throws DodpException {
        int statusCode = response.code();
        String bodyText;
        try {
            ResponseBody body = response.body();
            bodyText = body != null ? body.string() : "";
        } catch (IOException e) {
            throw new DodpTransportException("Failed to read response body for " + operation, e);
        }

        Document document;
        try {
            document = XmlUtil.parse(bodyText);
        } catch (RuntimeException e) {
            throw new DodpTransportException(
                    "HTTP " + statusCode + " returned a non-XML body for " + operation, e);
        }

        Element envelope = document.getDocumentElement();
        Element soapBody = XmlUtil.firstChildElement(envelope, DodpNamespaces.SOAP_ENVELOPE, "Body");
        if (soapBody == null) {
            throw new DodpTransportException("Missing SOAP Body in response for " + operation);
        }

        Element fault = XmlUtil.firstChildElement(soapBody, DodpNamespaces.SOAP_ENVELOPE, "Fault");
        if (fault != null) {
            throw toFaultException(fault);
        }

        Element result = XmlUtil.firstChildElement(soapBody);
        if (result == null) {
            throw new DodpTransportException("Empty SOAP Body in response for " + operation);
        }
        return result;
    }

    private DodpFaultException toFaultException(Element fault) {
        String faultString = XmlUtil.childText(fault, "faultstring");
        Element detail = XmlUtil.firstChildElement(fault, "detail");
        String faultName = null;
        String reason = null;
        if (detail != null) {
            Element detailChild = XmlUtil.firstChildElement(detail);
            if (detailChild != null) {
                faultName = detailChild.getLocalName();
                reason = XmlUtil.childText(detailChild, "reason");
            }
        }
        if (faultName == null) {
            faultName = faultString;
        }
        return DodpFaultException.fromFault(faultName, reason != null ? reason : faultString);
    }

    @Override
    public void close() {
        // Discard session cookies and release the OkHttp connection pool.
        cookieJar.clear();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
