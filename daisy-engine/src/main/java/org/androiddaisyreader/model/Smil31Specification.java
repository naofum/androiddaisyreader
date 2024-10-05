package org.androiddaisyreader.model;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.androiddaisyreader.model.XmlUtilities.mapUnsupportedEncoding;
import static org.androiddaisyreader.model.XmlUtilities.obtainEncodingStringFromInputStream;

public class Smil31Specification extends DefaultHandler {
    private Element current;
    private Part.Builder partBuilder;
    private List<Part> parts = new ArrayList<Part>();
    private BookContext context;

    private boolean handlingPar = false;

    //    private XmlModel model;
//    private List<XmlModel> listModel = new ArrayList<XmlModel>();
//    private static final int NUM_LEVELS_AVAILABLE_IN_DAISY30 = 6;
    private String id = null;
    private String previousHandleId = null;
    private String path = "";
    private String currentContentsFilename;
    private Document doc;
//    private StringBuilder buffer = new StringBuilder();

    /**
     * Create an object representing a EPUB version 3.3 Specification.
     *
     * @param context the BookContext used to locate references to files in the
     *                xhtml file.
     */
    public Smil31Specification(BookContext context) {
        this.context = context;
    }

    /**
     * Factory method that returns the Parts discovered in the contents.
     * <p>
     * TODO 20120303 (jharty): review the exception handling as all exceptions
     * are currently converted to RuntimeExceptions which makes them harder to
     * interpret by calling code. Note: this rework should be across the entire
     * body of code, not just for this method.
     *
     * @param context  BookContext used to locate files that comprise the book
     * @param contents The contents to parse to extract the Parts.
     * @return The parts discovered in the contents.
     */
    public static Part[] getParts(BookContext context, InputStream contents, String path) {
        String encoding = "utf-8";
        try {
            encoding = obtainEncodingStringFromInputStream(contents);
            encoding = mapUnsupportedEncoding(encoding);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return getParts(context, contents, path, encoding);
    }

    public static Part[] getParts(BookContext context, InputStream contents,String path, String encoding) {
        Smil31Specification smil = new Smil31Specification(context);
        smil.path = path;
        try {
            XMLReader saxParser = Smil.getSaxParser();
            saxParser.setContentHandler(smil);
            saxParser.parse(Smil.getInputSource(contents));
//            contents.close();

        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (contents != null) {
                    contents.close();
                }
            } catch (IOException e) {
                //
            }
        }
        return smil.getParts();
    }

    /**
     * Get the Parts discovered in this SMIL contents.
     *
     * @return The Parts.
     */
    private Part[] getParts() {
        return parts.toArray(new Part[0]);
    }

    private enum Element {
        H1, H2, H3, H4, H5, H6, SENT, LEVEL1, LEVEL2, LEVEL3, LEVEL4, LEVEL5, LEVEL6,
        SPAN, DIV, P, SECTION, BODY;

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }
    }

    private static Map<String, Element> elementMap = new HashMap<String, Element>(
            Element.values().length);

    static {
        for (Element e : Element.values()) {
            elementMap.put(e.toString(), e);
        }
    }

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {

        current = elementMap.get(ParserUtilities.getName(localName, name));
        if (current == null) {
            return;
        }

        switch (current) {
            case H1:
            case H2:
            case H3:
            case H4:
            case H5:
            case H6:
                handlingPar = false;
                addPartToSection();
                break;
            default:
                break;
        }
    }

    private void addPartToSection() {
        parts.add(partBuilder.build());
    }

    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes) {
        current = elementMap.get(ParserUtilities.getName(localName, name));
        if (current == null) {
            return;
        }

        if (getId(attributes) != null) {
            id = getId(attributes);
        }

        switch (current) {
            case H1:
            case H2:
            case H3:
            case H4:
            case H5:
            case H6:
                handlingPar = true;
                handlePar(attributes);
                partBuilder.setId(id);
                handleTextElement(path + "#" + id);
                break;
            case SPAN:
            case P:
            case DIV:
                handleTextElement(path + "#" + id);
                break;
            default:
                // Record the element(s) we don't handle in case we can improve our
                // processing of smil files.
                recordUnhandledElement(current, attributes);
                break;
        }
    }

    private void handlePar(Attributes attributes) {
        newPart();
//        String id = ParserUtilities.getValueForName("id", attributes);
//        partBuilder.setId(id);
//        if (getClass(attributes) != null && getClass(attributes).equals("prodnote")) {
//            isProdNote = true;
//        }
    }

    private void newPart() {
        partBuilder = new Part.Builder();
    }

    private String getId(Attributes attributes) {
        return ParserUtilities.getValueForName("id", attributes);
    }

    /**
     * Handle the Text Element.
     * <p>
     * The text element stores the location of a text fragment in an id
     * attribute.
     *
     * @param src
     */
    private void handleTextElement(String src) {
        // Create HTML Snippet Reader
        String[] elements = DaisySnippet.parseCompositeReference(src);
        String uri = elements[0];
        String id = elements[1];

        // We need to create the jsoup document if it's not initialised, or if
        // the filename has changed (which means the contents are no longer
        // valid.
        if (doc == null || !uri.equalsIgnoreCase(currentContentsFilename)) {
            InputStream contents = null;
            try {
                contents = context.getResource(uri);
                String encoding = obtainEncodingStringFromInputStream(contents);
                doc = Jsoup.parse(contents, encoding, context.getBaseUri());
                currentContentsFilename = uri;
            } catch (IOException ioe) {
                // TODO 20120214 (jharty): we need to consider more appropriate
                // error reporting.
                throw new RuntimeException("TODO fix me", ioe);
            } finally {
                try {
                    if (contents != null) {
                        contents.close();
                    }
                } catch (IOException e) {
                    //
                }
            }
        }
        if (previousHandleId == null || !previousHandleId.equals(id)) {
            partBuilder.addSnippet(new DaisySnippet(doc, id));
            previousHandleId = id;
        }
    }

    private void recordUnhandledElement(Element element, Attributes attributes) {
        StringBuilder elementDetails = new StringBuilder();
        elementDetails.append(String.format("[%s ", element.toString()));
        for (int i = 0; i < attributes.getLength(); i++) {
            elementDetails.append(String.format("%s=%s", attributes.getLocalName(i),
                    attributes.getValue(i)));
        }
        elementDetails.append("]");
    }

}
