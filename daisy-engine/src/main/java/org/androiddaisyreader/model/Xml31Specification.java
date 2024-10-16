package org.androiddaisyreader.model;

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

public class Xml31Specification extends DefaultHandler {
    private XmlModel model;
    private List<XmlModel> listModel = new ArrayList<XmlModel>();
    private static final int NUM_LEVELS_AVAILABLE_IN_DAISY202 = 6;
    private Element current;
    private StringBuilder buffer = new StringBuilder();

    public Xml31Specification() {
    }

    private enum Element {
        H1, H2, H3, H4, H5, H6, SENT, LEVEL1, LEVEL2, LEVEL3, LEVEL4, LEVEL5, LEVEL6,
        SPAN, CONVERTITLE, DOCTITLE, BODY;
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

    private static Map<Element, Integer> levelMap = new HashMap<Element, Integer>(
            NUM_LEVELS_AVAILABLE_IN_DAISY202);
    static {
        levelMap.put(Element.LEVEL1, 1);
        levelMap.put(Element.LEVEL2, 2);
        levelMap.put(Element.LEVEL3, 3);
        levelMap.put(Element.LEVEL4, 4);
        levelMap.put(Element.LEVEL5, 5);
        levelMap.put(Element.LEVEL6, 6);
        levelMap.put(Element.BODY, 1);
    }

    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes) {
        current = elementMap.get(ParserUtilities.getName(localName, name));
        if (current == null) {
            return;
        }

        switch (current) {
        case LEVEL1:
        case LEVEL2:
        case LEVEL3:
        case LEVEL4:
        case LEVEL5:
        case LEVEL6:
        case BODY:
            handleStartOfHeading(current, attributes);
            break;
        case H1:
        case H2:
        case H3:
        case H4:
        case H5:
        case H6:
        case CONVERTITLE:
//        case DOCTITLE:
            buffer.setLength(0);
            addSmilHref(attributes);
            break;
        case SENT:
            handleStartOfSend(attributes);
            break;
        case SPAN:
            buffer.setLength(0);
            handleStartOfSend(attributes);
//            addSmilHref(attributes);
            break;
        default:
            // do nothing for now for unmatched elements
            break;
        }
    }

    private void handleStartOfHeading(Element heading, Attributes attributes) {
        String id = getId(attributes);
        int level = levelMap.get(heading);
        model = new XmlModel();
        model.setId(id);
        model.setLevel(level);

    }

    private void addSmilHref(Attributes attributes) {
        String smilHref = getSmilHref(attributes);
        if (model != null && model.getId() == null) {
            model.setSmilHref(smilHref);
            model.setId(getId(attributes));
        }
    }

    private void handleStartOfSend(Attributes attributes) {
        String id = getId(attributes);
        model = new XmlModel();
        model.setId(id);
    }

    private void _handleStartOfSend(Attributes attributes) {
        if (model != null && model.getSmilHref() == null) {
            model.setSmilHref(getSmilHref(attributes));
            model.setId(getId(attributes));
        }
    }

    private String getId(Attributes attributes) {
        return ParserUtilities.getValueForName("id", attributes);
    }

    private String getSmilHref(Attributes attributes) {
        return ParserUtilities.getValueForName("smilref", attributes);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        super.characters(ch, start, length);
        buffer.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {

        // add current element type to the book model.
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
        case SPAN:
            addText();
            break;
        case LEVEL1:
        case LEVEL2:
        case LEVEL3:
        case LEVEL4:
        case LEVEL5:
        case LEVEL6:
            break;
        default:
            break;
        }
    }

    private void addText() {
        if (model != null) {
            if (model.getText() == null) {
                model.setText(buffer.toString());
            }
            listModel.add(model);
        }
    }

    private List<XmlModel> build() {
        return listModel;
    }

    public static List<XmlModel> readFromStream(InputStream contents) throws IOException {
        String encoding = obtainEncodingStringFromInputStream(contents);
        encoding = mapUnsupportedEncoding(encoding);
        return readFromStream(contents, encoding);

    }

    public static List<XmlModel> readFromStream(InputStream contents, String encoding)
            throws IOException {
        Xml31Specification specification = new Xml31Specification();
        try {
            XMLReader saxParser = Smil.getSaxParser();
            saxParser.setContentHandler(specification);
            saxParser.parse(Smil.getInputSource(contents));
//            contents.close();

        } catch (Exception e) {
            throw new IOException("Couldn't parse the xml file", e);
        } finally {
            try {
                if (contents != null) {
                    contents.close();
                }
            } catch (IOException e) {
                //
            }
        }
        return specification.build();
    }
}
