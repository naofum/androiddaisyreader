package com.github.library.dodp.model;

import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.library.dodp.DodpNamespaces.DAISY_ONLINE;

/**
 * Describes the reading system to the service via
 * {@code setReadingSystemAttributes}. Built with {@link Builder} and serialized
 * into the SOAP request by the client.
 */
public final class ReadingSystemAttributes {

    private final String manufacturer;
    private final String model;
    private final String serialNumber;
    private final String version;
    private final Config config;

    private ReadingSystemAttributes(String manufacturer, String model, String serialNumber,
                                    String version, Config config) {
        this.manufacturer = manufacturer;
        this.model = model;
        this.serialNumber = serialNumber;
        this.version = version;
        this.config = config;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getVersion() {
        return version;
    }

    public Config getConfig() {
        return config;
    }

    public Element toElement(Document document) {
        Element root = document.createElementNS(DAISY_ONLINE, "readingSystemAttributes");
        XmlUtil.appendChildText(root, DAISY_ONLINE, "manufacturer", manufacturer);
        XmlUtil.appendChildText(root, DAISY_ONLINE, "model", model);
        XmlUtil.appendChildText(root, DAISY_ONLINE, "serialNumber", serialNumber);
        XmlUtil.appendChildText(root, DAISY_ONLINE, "version", version);

        Element configElement = XmlUtil.appendChild(root, DAISY_ONLINE, "config");
        XmlUtil.appendChildText(configElement, DAISY_ONLINE, "supportsMultipleSelections",
                String.valueOf(config.supportsMultipleSelections));
        XmlUtil.appendChildText(configElement, DAISY_ONLINE, "preferredUILanguage", config.preferredUILanguage);
        if (config.bandwidth != null) {
            XmlUtil.appendChildText(configElement, DAISY_ONLINE, "bandwidth", String.valueOf(config.bandwidth));
        }

        Element formatsElement = XmlUtil.appendChild(configElement, DAISY_ONLINE, "supportedContentFormats");
        for (String format : config.supportedContentFormats) {
            XmlUtil.appendChildText(formatsElement, DAISY_ONLINE, "contentFormat", format);
        }

        Element protectionElement = XmlUtil.appendChild(configElement, DAISY_ONLINE, "supportedContentProtectionFormats");
        for (String protection : config.supportedContentProtectionFormats) {
            XmlUtil.appendChildText(protectionElement, DAISY_ONLINE, "protectionFormat", protection);
        }

        if (config.keyRing != null && !config.keyRing.isEmpty()) {
            Element keyRingElement = XmlUtil.appendChild(configElement, DAISY_ONLINE, "keyRing");
            for (String item : config.keyRing) {
                XmlUtil.appendChildText(keyRingElement, DAISY_ONLINE, "item", item);
            }
        }

        Element mimeTypesElement = XmlUtil.appendChild(configElement, DAISY_ONLINE, "supportedMimeTypes");
        for (MimeType mimeType : config.supportedMimeTypes) {
            Element mimeTypeElement = XmlUtil.appendChild(mimeTypesElement, DAISY_ONLINE, "mimeType");
            mimeTypeElement.setAttribute("type", mimeType.type);
            if (mimeType.language != null) {
                mimeTypeElement.setAttributeNS(Label.XML_NAMESPACE, "xml:lang", mimeType.language);
            }
        }

        Element inputTypesElement = XmlUtil.appendChild(configElement, DAISY_ONLINE, "supportedInputTypes");
        for (InputType inputType : config.supportedInputTypes) {
            Element inputElement = XmlUtil.appendChild(inputTypesElement, DAISY_ONLINE, "input");
            inputElement.setAttribute("type", inputType.name());
        }

        XmlUtil.appendChildText(configElement, DAISY_ONLINE, "requiresAudioLabels",
                String.valueOf(config.requiresAudioLabels));

        if (config.additionalTransferProtocols != null && !config.additionalTransferProtocols.isEmpty()) {
            Element protocolsElement = XmlUtil.appendChild(configElement, DAISY_ONLINE, "additionalTransferProtocols");
            for (String protocol : config.additionalTransferProtocols) {
                XmlUtil.appendChildText(protocolsElement, DAISY_ONLINE, "protocol", protocol);
            }
        }
        return root;
    }

    public static Builder builder(String manufacturer, String model, String version) {
        return new Builder(manufacturer, model, version);
    }

    /**
     * Reading system configuration block.
     */
    public static final class Config {

        private final boolean supportsMultipleSelections;
        private final String preferredUILanguage;
        private final Integer bandwidth;
        private final List<String> supportedContentFormats;
        private final List<String> supportedContentProtectionFormats;
        private final List<String> keyRing;
        private final List<MimeType> supportedMimeTypes;
        private final List<InputType> supportedInputTypes;
        private final boolean requiresAudioLabels;
        private final List<String> additionalTransferProtocols;

        private Config(boolean supportsMultipleSelections, String preferredUILanguage, Integer bandwidth,
                       List<String> supportedContentFormats, List<String> supportedContentProtectionFormats,
                       List<String> keyRing, List<MimeType> supportedMimeTypes, List<InputType> supportedInputTypes,
                       boolean requiresAudioLabels, List<String> additionalTransferProtocols) {
            this.supportsMultipleSelections = supportsMultipleSelections;
            this.preferredUILanguage = preferredUILanguage;
            this.bandwidth = bandwidth;
            this.supportedContentFormats = supportedContentFormats;
            this.supportedContentProtectionFormats = supportedContentProtectionFormats;
            this.keyRing = keyRing;
            this.supportedMimeTypes = supportedMimeTypes;
            this.supportedInputTypes = supportedInputTypes;
            this.requiresAudioLabels = requiresAudioLabels;
            this.additionalTransferProtocols = additionalTransferProtocols;
        }

        public boolean isSupportsMultipleSelections() {
            return supportsMultipleSelections;
        }

        public String getPreferredUILanguage() {
            return preferredUILanguage;
        }

        public Integer getBandwidth() {
            return bandwidth;
        }

        public List<String> getSupportedContentFormats() {
            return supportedContentFormats;
        }

        public List<String> getSupportedContentProtectionFormats() {
            return supportedContentProtectionFormats;
        }

        public List<String> getKeyRing() {
            return keyRing;
        }

        public List<MimeType> getSupportedMimeTypes() {
            return supportedMimeTypes;
        }

        public List<InputType> getSupportedInputTypes() {
            return supportedInputTypes;
        }

        public boolean isRequiresAudioLabels() {
            return requiresAudioLabels;
        }

        public List<String> getAdditionalTransferProtocols() {
            return additionalTransferProtocols;
        }
    }

    /**
     * A single supported mime type entry.
     */
    public static final class MimeType {

        private final String type;
        private final String language;

        public MimeType(String type, String language) {
            this.type = type;
            this.language = language;
        }

        public String getType() {
            return type;
        }

        public String getLanguage() {
            return language;
        }
    }

    /**
     * Fluent builder for {@link ReadingSystemAttributes}.
     */
    public static final class Builder {

        private final String manufacturer;
        private final String model;
        private final String version;
        private String serialNumber;
        private boolean supportsMultipleSelections;
        private String preferredUILanguage;
        private Integer bandwidth;
        private final List<String> supportedContentFormats = new ArrayList<>();
        private final List<String> supportedContentProtectionFormats = new ArrayList<>();
        private List<String> keyRing = Collections.emptyList();
        private final List<MimeType> supportedMimeTypes = new ArrayList<>();
        private final List<InputType> supportedInputTypes = new ArrayList<>();
        private boolean requiresAudioLabels;
        private List<String> additionalTransferProtocols = Collections.emptyList();

        private Builder(String manufacturer, String model, String version) {
            this.manufacturer = manufacturer;
            this.model = model;
            this.version = version;
        }

        public Builder serialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        public Builder supportsMultipleSelections(boolean supportsMultipleSelections) {
            this.supportsMultipleSelections = supportsMultipleSelections;
            return this;
        }

        public Builder preferredUILanguage(String preferredUILanguage) {
            this.preferredUILanguage = preferredUILanguage;
            return this;
        }

        public Builder bandwidth(Integer bandwidth) {
            this.bandwidth = bandwidth;
            return this;
        }

        public Builder addContentFormat(String format) {
            supportedContentFormats.add(format);
            return this;
        }

        public Builder addContentProtectionFormat(String protectionFormat) {
            supportedContentProtectionFormats.add(protectionFormat);
            return this;
        }

        public Builder keyRing(List<String> keyRing) {
            this.keyRing = new ArrayList<>(keyRing);
            return this;
        }

        public Builder addMimeType(String type, String language) {
            supportedMimeTypes.add(new MimeType(type, language));
            return this;
        }

        public Builder addInputType(InputType inputType) {
            supportedInputTypes.add(inputType);
            return this;
        }

        public Builder requiresAudioLabels(boolean requiresAudioLabels) {
            this.requiresAudioLabels = requiresAudioLabels;
            return this;
        }

        public Builder additionalTransferProtocols(List<String> protocols) {
            this.additionalTransferProtocols = new ArrayList<>(protocols);
            return this;
        }

        public ReadingSystemAttributes build() {
            Config config = new Config(supportsMultipleSelections, preferredUILanguage, bandwidth,
                    new ArrayList<>(supportedContentFormats),
                    new ArrayList<>(supportedContentProtectionFormats),
                    new ArrayList<>(keyRing),
                    new ArrayList<>(supportedMimeTypes),
                    new ArrayList<>(supportedInputTypes),
                    requiresAudioLabels,
                    new ArrayList<>(additionalTransferProtocols));
            return new ReadingSystemAttributes(manufacturer, model, serialNumber, version, config);
        }
    }
}
