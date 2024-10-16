package org.androiddaisyreader.model;

import static org.androiddaisyreader.model.XmlUtilities.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.input.ReaderInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parser to handle SMIL 1.0 files used by Daisy 2.02 books.
 * 
 * @author jharty
 */
public class Smil10Specification extends DefaultHandler {

    private Element current;
    private Part.Builder partBuilder;
    private List<Part> parts = new ArrayList<Part>();
    private BookContext context;

    private boolean handlingPar = false;

    private String currentContentsFilename;
    private Document doc;

    private static final int DAISYFORMAT202 = 202;

    /**
     * Create an object representing a SMIL version 1.0 Specification.
     * 
     * @param context the BookContext used to locate references to files in the
     *            SMIL file.
     */
    private Smil10Specification(BookContext context) {
        this.context = context;
    }

    /**
     * Factory method that returns the Parts discovered in the contents.
     * 
     * TODO 20120303 (jharty): review the exception handling as all exceptions
     * are currently converted to RuntimeExceptions which makes them harder to
     * interpret by calling code. Note: this rework should be across the entire
     * body of code, not just for this method.
     * 
     * @param context BookContext used to locate files that comprise the book
     * @param contents The contents to parse to extract the Parts.
     * @return The parts discovered in the contents.
     */
    public static Part[] getParts(BookContext context, InputStream contents) {
        String encoding = "utf-8";
        try {
            encoding = obtainEncodingStringFromInputStream(contents);
            encoding = mapUnsupportedEncoding(encoding);
            if (encoding.equals("shift_jis")) {
                contents = XmlUtilities.replaceXmlEncodingString(contents);
                contents = new BufferedInputStream(new ReaderInputStream(new InputStreamReader(contents, encoding), "utf-8"));
                encoding = "utf-8";
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return getParts(context, contents, encoding);
    }

    public static Part[] getParts(BookContext context, InputStream contents, String encoding) {
        Smil10Specification smil = new Smil10Specification(context);
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

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {

        current = elementMap.get(ParserUtilities.getName(localName, name));
        if (current == null) {
            return;
        }

        switch (current) {
        case PAR:
            handlingPar = false;
            addPartToSection();
            break;
        case SEQ:
            // do nothing
            break;
        case AUDIO:
        case TEXT:
            if (!handlingPar) {
                addPartToSection();
            }
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

        switch (current) {
        case AUDIO:
            if (!handlingPar) {
                newPart();
            }
            handleAudio(attributes);
            break;
        case META:
            handleMeta(attributes);
            break;
        case PAR:
            handlingPar = true;
            handlePar(attributes);
            break;
        case SEQ:
            // do nothing.
            break;
        case TEXT:
            if (!handlingPar) {
                newPart();
            }
            handleTextElement(attributes);
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
        String id = ParserUtilities.getValueForName("id", attributes);
        partBuilder.setId(id);
    }

    private void newPart() {
        partBuilder = new Part.Builder();
    }

    /**
     * Handle the Text Element.
     * 
     * The text element stores the location of a text fragment in an id
     * attribute.
     * 
     * @param attributes
     */
    private void handleTextElement(Attributes attributes) {
        String src = ParserUtilities.getValueForName("src", attributes);
        // TODO 20120207 (jharty) Refactor for a text reference into a html file
        // Create HTML Snippet Reader
        String[] elements = DaisySnippet.parseCompositeReference(src);
        String uri = elements[0];
        String id = elements[1];

        // We need to create the jsoup document if it's not initialised, or if
        // the filename has changed (which means the contents are no longer
        // valid).
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

        partBuilder.addSnippet(new DaisySnippet(doc, id));
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

    private void handleAudio(Attributes attributes) {
        // <audio src="file.mp3" clip-begin="npt=0.000s" clip-end="npt=3.578s"
        // id="audio_0001"/>
        String audioFilename = ParserUtilities.getValueForName("src", attributes);
        int clipBegin = ExtractTimingValues.extractTimingAsMilliSeconds("clip-begin", attributes,
                DAISYFORMAT202);
        int clipEnd = ExtractTimingValues.extractTimingAsMilliSeconds("clip-end", attributes,
                DAISYFORMAT202);
        String id = ParserUtilities.getValueForName("id", attributes);

        Audio audio = new Audio(id, audioFilename, clipBegin, clipEnd);
        partBuilder.addAudio(audio);
    }

    private void handleMeta(Attributes attributes) {
        String metaName = Smil.handleMeta(attributes);
        Meta meta = metaMap.get(metaName);
        if (meta == null) {
            return;
        }
    }

    private enum Element {
        AUDIO, META, PAR, SEQ, TEXT;
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

    private enum Meta {
        FORMAT {
            @Override
            public String toString() {
                return "dc:format";
            }
        }

        // Add more enums as we need them.
    }

    private static Map<String, Meta> metaMap = new HashMap<String, Meta>(Meta.values().length);
    static {
        for (Meta m : Meta.values()) {
            metaMap.put(m.toString(), m);
        }
    }
}
