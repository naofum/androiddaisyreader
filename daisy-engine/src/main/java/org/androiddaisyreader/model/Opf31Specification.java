package org.androiddaisyreader.model;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Opf31Specification extends DefaultHandler {
    private Element current;
    // private Stack<Daisy30Section.Builder> headingStack = new
    // Stack<Daisy30Section.Builder>();
    private Map<String, String> manifestItem = new HashMap<String, String>();
    // TODO 20120124 (jharty):replace with something that doesn't use Vector
    private StringBuilder buffer = new StringBuilder();
    private List<XmlModel> listModel = new ArrayList<XmlModel>();
    private List<XmlModel> allModel = new ArrayList<XmlModel>();
    private DaisyBook.Builder bookBuilder = new DaisyBook.Builder();
    private BookContext bookContext;

    public Opf31Specification(BookContext bookContext) {
        this.bookContext = bookContext;
    }

    private enum Element {
        A, METADATA, ITEM, ITEMREF, SPINE;
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

    private static Map<String, Smil.Meta> metaMap = new HashMap<String, Smil.Meta>(
            Smil.Meta.values().length);
    static {
        for (Smil.Meta m : Smil.Meta.values()) {
            metaMap.put(m.toString(), m);
        }
    }

    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes) {
        current = elementMap.get(ParserUtilities.getName(localName, name));

        if (name.contains("dc")) {
            buffer.setLength(0);
        }
        if (current == null) {
            return;
        }

        switch (current) {
        case ITEM:
            buffer.setLength(0);
            handleItemOfHeading(attributes);
            break;
        case ITEMREF:
            handleStartOfSpine(attributes);
            break;
        case SPINE:
            buffer.setLength(0);
            break;
        default:
            // do nothing for now for unmatched elements
            break;
        }
    }

    private void handleItemOfHeading(Attributes attributes) {
        String id = getId(attributes);
        String href = getHref(attributes);
        String mediaOverlay = getMediaOverlay(attributes);
        if (href.endsWith("html") && bookBuilder != null) {
            InputStream contents = null;
            try {
                contents = bookContext.getResource(href);
                listModel = Xml31Specification.readFromStream(contents, null);
                // set model-overlay id to smilHref temporary
                for (XmlModel model : listModel) {
                    model.setSmilHref(id);
                }
                allModel.addAll(listModel);
            } catch (IOException e) {

            } finally {
                try {
                    if (contents != null) {
                        contents.close();
                    }
                } catch (IOException e) {
                    //
                }
            }
            manifestItem.put(id + "_ref", mediaOverlay);
        }
        manifestItem.put(id, href);
    }

    private void handleStartOfSpine(Attributes attributes) {
        // Create the new header
        String id = getIdRef(attributes);
        String linear = getLinear(attributes);
        if (linear != null && linear.equals("no")) {
            return;
        }
        XmlModel model = getXmlModelBySmilHref(getIdRef(attributes));
//        String smilId = manifestItem.get(getIdRef(attributes));
//        String smilHref = manifestItem.get(smilId);
//        String smilId = getIdRef(attributes);
        String smilHref = manifestItem.get(id);
        String smilId = manifestItem.get(id + "_ref");
        if (smilId != null && !smilId.isEmpty()) {
            smilHref = manifestItem.get(smilId);
        }
        if (model != null && model.getId() != null) {
            model.setSmilHref(smilHref + "#" + model.getId());
//            model.setSmilHref(smilHref + "#" + (model.getId() == null ? "" : model.getId()));
            attachSectionToParent(model);
        }

//        for (XmlModel model : listModel) {
//            String id = model.getSmilHref();
//            if (id != null && id.equals(getIdRef(attributes))) {
//                String mediaOverlay = manifestItem.get(id + "_ref");
//                String smilHref = manifestItem.get(mediaOverlay);
//                XmlModel tmp = new XmlModel();
//                tmp.setId(model.getId());
//                tmp.setLevel(model.getLevel());
//                tmp.setText(model.getText());
//                tmp.setSmilHref(smilHref + "#" + model.getId());
//                attachSectionToParent(tmp);
//            }
//        }
    }

    private void attachSectionToParent(XmlModel model) {
        DaisySection.Builder builder = new DaisySection.Builder().setContext(bookContext);
        if (model != null) {
            builder.setId(model.getId());
            builder.setLevel(model.getLevel());
            builder.setTitle(model.getText());
            builder.setHref(model.getSmilHref());
            Section sibbling = builder.build();
            bookBuilder.addSection(sibbling);
            listModel.remove(model);
        }
    }

    private XmlModel getXmlModelBySmilHref(String smilHref) {
        XmlModel result = null;
        for (XmlModel model : allModel) {
//            if (model.getSmilHref() != null && model.getSmilHref().contains(smilHref)) {
            if (model.getSmilHref() != null && model.getSmilHref().equals(smilHref)) {
                result = model;
                break;
            }
        }
        return result;
    }

    private String getId(Attributes attributes) {
        return ParserUtilities.getValueForName("id", attributes);
    }

    private String getHref(Attributes attributes) {
        return ParserUtilities.getValueForName("href", attributes);
    }

    private String getIdRef(Attributes attributes) {
        return ParserUtilities.getValueForName("idref", attributes);
    }

    private String getMediaOverlay(Attributes attributes) {
        return ParserUtilities.getValueForName("media-overlay", attributes);
    }

    private String getLinear(Attributes attributes) {
        return ParserUtilities.getValueForName("linear", attributes);
    }

    private void handleMetadata(String tagName) {
        String content = buffer.toString();
        Smil.Meta meta = metaMap.get(tagName.toLowerCase());

        if (meta == null) {
            return;
        }
        switch (meta) {
        case DATE:
            try {
                Date date = Smil.parseDate(content, null);
                bookBuilder.setDate(date);
            } catch (IllegalArgumentException e) {
                //
            }
            break;
        case TITLE:
            bookBuilder.setTitle(content);
            break;
        // Added by Logigear to resolve case: the daisy book is not audio.
        // Date: Jun-13-2013
        case TOTALTIME:
            // bookBuilder.setTotalTime(content);
            // System.out.println("TOTALTIME " + content);
            break;
        case CREATOR:
            bookBuilder.setCreator(content);
            break;
        case PUBLISHER:
            bookBuilder.setPublisher(content);
            break;

        default:
            // this handles null (apparently :)
            break;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        super.characters(ch, start, length);
        buffer.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
        current = elementMap.get(ParserUtilities.getName(localName, name));
        if (name.contains("dc")) {
            handleMetadata(name);
        }
        if (current == null) {
            return;
        }
        switch (current) {
        case SPINE:
// 20240920
//            while (listModel.size() > 0) {
//                attachSectionToParent(listModel.get(0));
//            }
            break;
        default:
            break;
        }

    }

    public DaisyBook build() {
        return bookBuilder.build();
    }

    public static DaisyBook readFromStream(InputStream contents,
            BookContext bookContext) throws IOException {
        Opf31Specification specification = new Opf31Specification(bookContext);
        try {
            XMLReader saxParser = Smil.getSaxParser();
            saxParser.setContentHandler(specification);
            saxParser.parse(Smil.getInputSource(contents));
//            contents.close();

        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Couldn't parse the opf contents.", e);
        } finally {
            if (contents != null) {
                contents.close();
            }
        }
        return specification.build();
    }
}
