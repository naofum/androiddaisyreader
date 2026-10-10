package com.github.library.dodp.download;

import com.github.library.dodp.model.Resource;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

/**
 * Downloads content resources over HTTP(S). Supports:
 * <ul>
 *   <li>HTTP Basic authentication embedded in the resource URI
 *       ({@code user:password@host}), sent only over HTTPS per the DODP
 *       security requirement;</li>
 *   <li>resumable downloads via HTTP {@code Range} requests, falling back to a
 *       full download when the server does not support ranges (HTTP 200 or
 *       416).</li>
 * </ul>
 *
 * <p>Implemented on OkHttp so it runs on Android (where {@code java.net.http}
 * is not available).</p>
 */
public final class ContentDownloader {

    private static final String USER_AGENT = "dodp-client/1.0";

    private final OkHttpClient httpClient;

    public ContentDownloader() {
        this(new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build());
    }

    public ContentDownloader(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Path download(Resource resource, Path destination) throws IOException {
        return download(resource, destination, true, null);
    }

    /**
     * Downloads a resource to {@code destination}.
     *
     * @param resume if true, an existing partial file is resumed using a Range
     *               request when the server supports it
     */
    public Path download(Resource resource, Path destination, boolean resume,
                         DownloadProgressListener listener) throws IOException {
        long existing = resume && Files.exists(destination) ? Files.size(destination) : 0L;

        Request request = buildRequest(resource, existing);
        Response response;
        try {
            response = httpClient.newCall(request).execute();
        } catch (IOException e) {
            if (listener != null) {
                listener.onFailed(e);
            }
            throw e;
        }

        try {
            int status = response.code();
            if (status == 416) {
                // Range not satisfiable: restart from scratch with a full request.
                response.close();
                Files.deleteIfExists(destination);
                Request freshRequest = buildRequest(resource, 0L);
                try (Response freshResponse = httpClient.newCall(freshRequest).execute()) {
                    if (freshResponse.code() != 200) {
                        throw new IOException("Unexpected HTTP status " + freshResponse.code()
                                + " after 416 for " + resource.getUri());
                    }
                    return copyBody(resource, freshResponse, destination, false, 0L, listener);
                }
            }
            if (status == 206) {
                return copyBody(resource, response, destination, true, existing, listener);
            }
            if (status == 200) {
                return copyBody(resource, response, destination, false, 0L, listener);
            }
            throw new IOException("Unexpected HTTP status " + status + " for " + resource.getUri());
        } finally {
            response.close();
        }
    }

    private Request buildRequest(Resource resource, long rangeStart) {
        URI uri = URI.create(resource.getUri());
        HttpUrl httpUrl = HttpUrl.get(uri);
        Request.Builder builder = new Request.Builder()
                .url(httpUrl)
                .header("User-Agent", USER_AGENT)
                .get();

        String userInfo = uri.getUserInfo();
        if (userInfo != null && userInfo.contains(":") && "https".equalsIgnoreCase(uri.getScheme())) {
            String encoded = Base64.getEncoder().encodeToString(userInfo.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }

        if (rangeStart > 0) {
            builder.header("Range", "bytes=" + rangeStart + "-");
        }
        return builder.build();
    }

    private Path copyBody(Resource resource, Response response, Path destination,
                          boolean append, long offset, DownloadProgressListener listener) throws IOException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }

        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Empty response body for " + resource.getUri());
        }

        long contentLength = body.contentLength();
        long total = contentLength >= 0 ? offset + contentLength : resource.getSize();

        StandardOpenOption[] options = append
                ? new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                : new StandardOpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

        long downloaded = offset;
        try (InputStream in = body.byteStream();
             OutputStream out = Files.newOutputStream(destination, options)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (listener != null) {
                    listener.onProgress(downloaded, total);
                }
            }
        }
        if (listener != null) {
            listener.onCompleted(destination);
        }
        return destination;
    }
}
