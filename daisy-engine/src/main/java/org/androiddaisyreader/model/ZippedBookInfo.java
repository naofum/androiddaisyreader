package org.androiddaisyreader.model;

import static org.androiddaisyreader.model.XmlUtilities.mapUnsupportedEncoding;
import static org.androiddaisyreader.model.XmlUtilities.obtainEncodingStringFromInputStream;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZippedBookInfo extends DefaultHandler {
    private Element current;
    private StringBuilder buffer = new StringBuilder();
    private DaisyBookInfo daisyBookInfo = new SimpleDaisyBookInfo();

    private enum Element {
        A, HTML, META, TITLE, H1, H2, H3, H4, H5, H6, SPAN;
        @Override
        public String toString() {
            return this.name().toLowerCase();
        }
    }

    private final Map<String, Element> elementMap = new HashMap<String, Element>(
            Element.values().length);
    {
        for (Element e : Element.values()) {
            elementMap.put(e.toString(), e);
        }
    }

    private final Map<String, Smil.Meta> metaMap = new HashMap<String, Smil.Meta>(
            Smil.Meta.values().length);
    {
        for (Smil.Meta m : Smil.Meta.values()) {
            metaMap.put(m.toString(), m);
        }
    }

//    public void DaisyBookInfo() {
//        daisyBookInfo = new SimpleDaisyBookInfo();
//    }

    private DaisyBookInfo getBookInfo() {
        return daisyBookInfo;
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
            case A:
            case H1:
            case H2:
            case H3:
            case H4:
            case H5:
            case H6:
            case SPAN:
                break;
            case META:
                handleMeta(attributes);
                break;
            default:
                // do nothing for now for unmatched elements
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {

        // add current element type to the book model.
        current = elementMap.get(ParserUtilities.getName(localName, name));
        if (name.contains("dc")) {
            handleMetadata(name);
        }
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
            case HTML:
                break;
            default:
                break;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        super.characters(ch, start, length);
        buffer.append(ch, start, length);
    }


    private void handleMeta(Attributes attributes) {
        String metaName = null;
        String content = null;
        String scheme = null;

        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.getLocalName(i);
            // The following code fixes the failure in travis-ci. Newer parsers
            // don't return data for getLocalName. Instead they return the
            // Qualified Name.
            if (name.length() == 0) {
                name = attributes.getQName(i);
            }

            if (name.equalsIgnoreCase("name") || name.equalsIgnoreCase("Content-type")) {
                metaName = attributes.getValue(i);
            }

            if (name.equalsIgnoreCase("content")) {
                content = attributes.getValue(i);
            }

            if (name.equalsIgnoreCase("scheme")) {
                scheme = attributes.getValue(i);
            }
        }

        Smil.Meta meta = metaMap.get(metaName);
        if (meta == null) {
            return;
        }

        switch (meta) {
            case DATE:
                daisyBookInfo.setDate(content);
                break;
            case TITLE:
                daisyBookInfo.setTitle(content);
                break;
            // Added by Logigear to resolve case: the daisy book is not audio.
            // Date: Jun-13-2013
            case CREATOR:
                daisyBookInfo.setAuthor(content);
                break;
            case PUBLISHER:
                daisyBookInfo.setPublisher(content);
                break;

            default:
                // this handles null (apparently :)
        }
    }

    private void handleMetadata(String tagName) {
        String content = buffer.toString();
        Smil.Meta meta = metaMap.get(tagName.toLowerCase());

        if (meta == null) {
            return;
        }
        switch (meta) {
            case DATE:
                daisyBookInfo.setDate(content);
                break;
            case TITLE:
                daisyBookInfo.setTitle(content);
                break;
            // Added by Logigear to resolve case: the daisy book is not audio.
            // Date: Jun-13-2013
            case CREATOR:
                daisyBookInfo.setAuthor(content);
                break;
            case PUBLISHER:
                daisyBookInfo.setPublisher(content);
                break;

            default:
                // this handles null (apparently :)
                break;
        }
    }


    public static DaisyBookInfo readFromStream(InputStream contents) throws IOException {
        String encoding = obtainEncodingStringFromInputStream(contents);
        encoding = mapUnsupportedEncoding(encoding);
//        System.out.println(encoding);
        return readFromStream(contents, encoding);
    }

    public static DaisyBookInfo readFromStream(InputStream contents, String encoding)
            throws IOException {
        ZippedBookInfo specification = new ZippedBookInfo();
        try {
            XMLReader saxParser = Smil.getSaxParser();
            saxParser.setContentHandler(specification);
            saxParser.parse(new InputSource(new InputStreamReader(contents, encoding)));

        } catch (Exception e) {
            throw new IOException("Couldn't parse the ncc.html or .opf contents.", e);
        } finally {
            try {
                if (contents != null) {
                    contents.close();
                }
            } catch (IOException e) {
                //
            }
        }
        return specification.getBookInfo();
    }

    public static DaisyBookInfo readFromStream2(InputStream contents) throws IOException {
        String encoding = obtainEncodingStringFromInputStream(contents);
        encoding = mapUnsupportedEncoding(encoding);
//        System.out.println(encoding);
        return readFromStream2(contents, encoding);
    }

    public static DaisyBookInfo readFromStream2(InputStream contents, String encoding)
            throws IOException {
        ZippedBookInfo specification = new ZippedBookInfo();
        ((SimpleDaisyBookInfo)specification.daisyBookInfo).setDaisy202(true);
        try {
            XMLReader saxParser = Smil.getSaxParser();
            saxParser.setContentHandler(specification);
            saxParser.parse(new InputSource(new InputStreamReader(contents, encoding)));

        } catch (Exception e) {
            throw new IOException("Couldn't parse the ncc.html or .opf contents.", e);
        } finally {
            try {
                if (contents != null) {
                    contents.close();
                }
            } catch (IOException e) {
                //
            }
        }
        return specification.getBookInfo();
    }

    public static DaisyBookInfo readFromZipStream(InputStream zipContents) throws IOException {
        ZipEntry zipEntry;
        ZipInputStream contents = new ZipInputStream(zipContents);
        zipEntry = contents.getNextEntry();
        while (zipEntry != null) {
            String name = zipEntry.getName();
            if (name.toLowerCase().endsWith("ncc.html")) {
                return readFromStream2(new BufferedInputStream(contents));
            } else if (name.toLowerCase().endsWith(".opf")) {
                return readFromStream(new BufferedInputStream(contents));
            }
            zipEntry = contents.getNextEntry();
        }
        return null;
    }

}
