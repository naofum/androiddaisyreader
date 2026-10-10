package com.github.library.dodp;

/**
 * Well-known XML namespaces used by the DAISY Online Delivery Protocol v1.
 */
public final class DodpNamespaces {

    /** DAISY Online protocol types. */
    public static final String DAISY_ONLINE = "http://www.daisy.org/ns/daisy-online/";

    /** SOAP 1.1 envelope. */
    public static final String SOAP_ENVELOPE = "http://schemas.xmlsoap.org/soap/envelope/";

    /** Z39.86-2005 Portable Bookmarks. */
    public static final String BOOKMARK = "http://www.daisy.org/z3986/2005/bookmark/";

    /** Dublin Core Metadata Element Set 1.1. */
    public static final String DC = "http://purl.org/dc/elements/1.1/";

    /** PDTB2 (DAISY protected digital talking book) key exchange. */
    public static final String PDTB2 = "http://www.daisy.org/DRM/2005/KeyExchange";

    private DodpNamespaces() {
    }
}
