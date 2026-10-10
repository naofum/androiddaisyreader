package com.github.library.dodp.model;

import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

import static com.github.library.dodp.DodpNamespaces.DAISY_ONLINE;

/**
 * Capabilities advertised by the service via {@code getServiceAttributes}.
 */
public final class ServiceAttributes {

    private final String serviceProviderId;
    private final Label serviceProviderLabel;
    private final String serviceId;
    private final Label serviceLabel;
    private final List<ContentSelectionMethod> supportedContentSelectionMethods;
    private final boolean supportsServerSideBack;
    private final boolean supportsSearch;
    private final List<String> supportedUplinkAudioCodecs;
    private final boolean supportsAudioLabels;
    private final List<OptionalOperation> supportedOptionalOperations;

    public ServiceAttributes(String serviceProviderId, Label serviceProviderLabel, String serviceId,
                             Label serviceLabel, List<ContentSelectionMethod> supportedContentSelectionMethods,
                             boolean supportsServerSideBack, boolean supportsSearch,
                             List<String> supportedUplinkAudioCodecs, boolean supportsAudioLabels,
                             List<OptionalOperation> supportedOptionalOperations) {
        this.serviceProviderId = serviceProviderId;
        this.serviceProviderLabel = serviceProviderLabel;
        this.serviceId = serviceId;
        this.serviceLabel = serviceLabel;
        this.supportedContentSelectionMethods = supportedContentSelectionMethods;
        this.supportsServerSideBack = supportsServerSideBack;
        this.supportsSearch = supportsSearch;
        this.supportedUplinkAudioCodecs = supportedUplinkAudioCodecs;
        this.supportsAudioLabels = supportsAudioLabels;
        this.supportedOptionalOperations = supportedOptionalOperations;
    }

    public String getServiceProviderId() {
        return serviceProviderId;
    }

    public Label getServiceProviderLabel() {
        return serviceProviderLabel;
    }

    public String getServiceId() {
        return serviceId;
    }

    public Label getServiceLabel() {
        return serviceLabel;
    }

    public List<ContentSelectionMethod> getSupportedContentSelectionMethods() {
        return supportedContentSelectionMethods;
    }

    public boolean isSupportsServerSideBack() {
        return supportsServerSideBack;
    }

    public boolean isSupportsSearch() {
        return supportsSearch;
    }

    public List<String> getSupportedUplinkAudioCodecs() {
        return supportedUplinkAudioCodecs;
    }

    public boolean isSupportsAudioLabels() {
        return supportsAudioLabels;
    }

    public List<OptionalOperation> getSupportedOptionalOperations() {
        return supportedOptionalOperations;
    }

    public boolean supports(OptionalOperation operation) {
        return supportedOptionalOperations.contains(operation);
    }

    public static ServiceAttributes parse(Element element) {
        Element provider = XmlUtil.firstChildElement(element, DAISY_ONLINE, "serviceProvider");
        String providerId = provider == null ? null : XmlUtil.attribute(provider, "id");
        Label providerLabel = null;
        if (provider != null) {
            Element labelElement = XmlUtil.firstChildElement(provider, DAISY_ONLINE, "label");
            if (labelElement != null) {
                providerLabel = Label.parse(labelElement);
            }
        }

        Element service = XmlUtil.firstChildElement(element, DAISY_ONLINE, "service");
        String serviceId = service == null ? null : XmlUtil.attribute(service, "id");
        Label serviceLabel = null;
        if (service != null) {
            Element labelElement = XmlUtil.firstChildElement(service, DAISY_ONLINE, "label");
            if (labelElement != null) {
                serviceLabel = Label.parse(labelElement);
            }
        }

        List<ContentSelectionMethod> methods = new java.util.ArrayList<>();
        Element methodsElement = XmlUtil.firstChildElement(element, DAISY_ONLINE, "supportedContentSelectionMethods");
        if (methodsElement != null) {
            for (Element methodElement : XmlUtil.childElements(methodsElement, DAISY_ONLINE, "method")) {
                ContentSelectionMethod method = ContentSelectionMethod.fromString(methodElement.getTextContent());
                if (method != null) {
                    methods.add(method);
                }
            }
        }

        boolean supportsServerSideBack = XmlUtil.parseBoolean(
                XmlUtil.childText(element, DAISY_ONLINE, "supportsServerSideBack"));
        boolean supportsSearch = XmlUtil.parseBoolean(
                XmlUtil.childText(element, DAISY_ONLINE, "supportsSearch"));

        List<String> codecs = Collections.emptyList();
        Element codecsElement = XmlUtil.firstChildElement(element, DAISY_ONLINE, "supportedUplinkAudioCodecs");
        if (codecsElement != null) {
            codecs = XmlUtil.childTexts(codecsElement, DAISY_ONLINE, "codec");
        }

        boolean supportsAudioLabels = XmlUtil.parseBoolean(
                XmlUtil.childText(element, DAISY_ONLINE, "supportsAudioLabels"));

        List<OptionalOperation> operations = new java.util.ArrayList<>();
        Element operationsElement = XmlUtil.firstChildElement(element, DAISY_ONLINE, "supportedOptionalOperations");
        if (operationsElement != null) {
            for (Element operationElement : XmlUtil.childElements(operationsElement, DAISY_ONLINE, "operation")) {
                OptionalOperation operation = OptionalOperation.fromString(operationElement.getTextContent());
                if (operation != null) {
                    operations.add(operation);
                }
            }
        }

        return new ServiceAttributes(providerId, providerLabel, serviceId, serviceLabel, methods,
                supportsServerSideBack, supportsSearch, codecs, supportsAudioLabels, operations);
    }
}
