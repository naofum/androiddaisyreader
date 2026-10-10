package com.github.library.dodp.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small namespace-aware DOM helpers for building and reading the XML that makes
 * up DODP SOAP messages. XML external entity processing is disabled to avoid
 * XXE attacks when parsing server responses.
 */
public final class XmlUtil {

    private XmlUtil() {
    }

    public static DocumentBuilderFactory newDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        // Android の DocumentBuilderFactory 実装は setXIncludeAware /
        // setExpandEntityReferences で UnsupportedOperationException を投げることがある。
        // XXE 対策の本命は disallow-doctype-decl（下の trySetFeature）なので、
        // これらは設定できなくても処理を継続する。
        trySetXIncludeAware(factory, false);
        trySetExpandEntityReferences(factory, false);
        trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory;
    }

    private static void trySetXIncludeAware(DocumentBuilderFactory factory, boolean value) {
        try {
            factory.setXIncludeAware(value);
        } catch (RuntimeException ignored) {
            // Android の一部実装では未サポート。無視して継続する。
        }
    }

    private static void trySetExpandEntityReferences(DocumentBuilderFactory factory, boolean value) {
        try {
            factory.setExpandEntityReferences(value);
        } catch (RuntimeException ignored) {
            // 未サポートの場合は無視して継続する。
        }
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
            // Not all parsers support every feature; ignore.
        }
    }

    public static DocumentBuilder newDocumentBuilder() {
        try {
            DocumentBuilder builder = newDocumentBuilderFactory().newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(org.xml.sax.SAXParseException exception) {
                }

                @Override
                public void error(org.xml.sax.SAXParseException exception) {
                }

                @Override
                public void fatalError(org.xml.sax.SAXParseException exception) {
                }
            });
            return builder;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create a DOM document builder", e);
        }
    }

    public static Document newDocument() {
        return newDocumentBuilder().newDocument();
    }

    public static Document parse(String xml) {
        return parse(xml.getBytes(StandardCharsets.UTF_8));
    }

    public static Document parse(byte[] xml) {
        try {
            return newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse XML document", e);
        }
    }

    public static String serialize(Node node) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize XML node", e);
        }
    }

    public static Element appendChild(Element parent, String namespaceURI, String qualifiedName) {
        Element element = parent.getOwnerDocument().createElementNS(namespaceURI, qualifiedName);
        parent.appendChild(element);
        return element;
    }

    public static Element appendChildText(Element parent, String namespaceURI, String qualifiedName, String text) {
        Element element = appendChild(parent, namespaceURI, qualifiedName);
        if (text != null) {
            element.setTextContent(text);
        }
        return element;
    }

    public static void setAttribute(Element element, String namespaceURI, String name, String value) {
        if (namespaceURI == null || namespaceURI.isEmpty()) {
            element.setAttribute(name, value);
        } else {
            element.setAttributeNS(namespaceURI, name, value);
        }
    }

    /** All element children regardless of namespace. */
    public static List<Element> childElements(Element parent) {
        NodeList nodes = parent.getChildNodes();
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) node);
            }
        }
        return result;
    }

    /** Element children matching the given local name (any namespace). */
    public static List<Element> childElements(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Element child : childElements(parent)) {
            if (localName.equals(child.getLocalName())) {
                result.add(child);
            }
        }
        return result;
    }

    /** Element children matching namespace URI and local name. */
    public static List<Element> childElements(Element parent, String namespaceURI, String localName) {
        List<Element> result = new ArrayList<>();
        for (Element child : childElements(parent)) {
            if (localName.equals(child.getLocalName()) && namespaceURI.equals(child.getNamespaceURI())) {
                result.add(child);
            }
        }
        return result;
    }

    /** First element child matching the given local name (any namespace). */
    public static Element firstChildElement(Element parent, String localName) {
        for (Element child : childElements(parent)) {
            if (localName.equals(child.getLocalName())) {
                return child;
            }
        }
        return null;
    }

    /** First element child matching namespace URI and local name. */
    public static Element firstChildElement(Element parent, String namespaceURI, String localName) {
        for (Element child : childElements(parent)) {
            if (localName.equals(child.getLocalName()) && namespaceURI.equals(child.getNamespaceURI())) {
                return child;
            }
        }
        return null;
    }

    public static Element firstChildElement(Element parent) {
        List<Element> children = childElements(parent);
        return children.isEmpty() ? null : children.get(0);
    }

    /** Text content of the first child element with the given local name (any namespace). */
    public static String childText(Element parent, String localName) {
        Element child = firstChildElement(parent, localName);
        return child == null ? null : child.getTextContent();
    }

    public static String childText(Element parent, String namespaceURI, String localName) {
        Element child = firstChildElement(parent, namespaceURI, localName);
        return child == null ? null : child.getTextContent();
    }

    public static String attribute(Element element, String localName) {
        if (!element.hasAttributes()) {
            return null;
        }
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Node attr = element.getAttributes().item(i);
            if (localName.equals(attr.getLocalName())) {
                return attr.getNodeValue();
            }
        }
        return null;
    }

    public static boolean hasAttribute(Element element, String localName) {
        return attribute(element, localName) != null;
    }

    public static boolean parseBoolean(String value) {
        return value != null && ("true".equals(value) || "1".equals(value));
    }

    public static Integer parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long parseLong(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<String> childTexts(Element parent, String localName) {
        List<Element> children = childElements(parent, localName);
        if (children.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(children.size());
        for (Element child : children) {
            result.add(child.getTextContent());
        }
        return result;
    }

    public static List<String> childTexts(Element parent, String namespaceURI, String localName) {
        List<Element> children = childElements(parent, namespaceURI, localName);
        if (children.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(children.size());
        for (Element child : children) {
            result.add(child.getTextContent());
        }
        return result;
    }
}
