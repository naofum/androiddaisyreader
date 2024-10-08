package org.androiddaisyreader.model;

import static org.androiddaisyreader.model.XmlUtilities.obtainEncodingStringFromInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

public class DaisySnippet extends Snippet {
    private Document doc;
    private String id;
    private boolean fin;
    private boolean suppress;
    private String imgSrc;
    private BookContext bookContext;
    private StringBuilder stringBuilder = new StringBuilder(500);

    // Prevent people from using the default constructor.
    @SuppressWarnings("unused")
    private DaisySnippet() {
    }

    /**
     * Create a DAISY 2.02 snippet.
     * 
     * Uses a jsoup document and the id part of a composite reference. This
     * constructor should be significantly faster than the one that creates a
     * jsoup document.
     * 
     * @param doc the jsoup representation of the HTML document
     * @param id the id used to get the text.
     */
    DaisySnippet(Document doc, String id) {
        this.doc = doc;
        this.id = id;
    }

    /**
     * Create a DAISY 2.02 snippet. Uses the book's context & a composite
     * reference.
     * 
     * The context may be a file location, an index into a zip file, etc. The
     * context is needed as composite references contain relative references.
     * 
     * A composite reference is formatted as follows:
     * fire_safety.html#dol_1_4_rgn_cnt_0043 An example of the reference in
     * context follows: <text src="fire_safety.html#dol_1_4_rgn_cnt_0043"
     * id="rgn_txt_0004_0017"/>
     * 
     * @param context
     * @param compositeReference
     */
    DaisySnippet(BookContext context, String compositeReference) {
        if (context == null) {
            throw new IllegalArgumentException("Programming error: context needs to be set");
        }

        bookContext = context;
        String[] elements = parseCompositeReference(compositeReference);
        String uri = elements[0];
        this.id = elements[1];
        InputStream contents = null;
        try {
            contents = context.getResource(uri);
            String encoding = obtainEncodingStringFromInputStream(contents);
            doc = Jsoup.parse(contents, encoding, context.getBaseUri());
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

    /**
     * Split a composite reference into the constituent parts.
     * 
     * A composite reference is formatted as follows:
     * fire_safety.html#dol_1_4_rgn_cnt_0043 An example of the reference in
     * context follows: <text src="fire_safety.html#dol_1_4_rgn_cnt_0043"
     * id="rgn_txt_0004_0017"/>
     * 
     * @param compositeReference to split
     * @return 2 strings, the first [0] contains the relative filename, the
     *         second [1] contains the id.
     * @throws IllegalArgumentException if the composite reference doesn't match
     *             the expected structure.
     */
    public static String[] parseCompositeReference(String compositeReference) {
        String[] elements = compositeReference.split("#");
        if (elements.length != 2) {
            throw new IllegalArgumentException(
                    "Expected composite reference in the form uri#id, got " + compositeReference);
        }
        return elements;
    }

    /**
     * Retrieve text from specified id to another id element
     *
     * @return string result
     */
    @Override
    public String getText() {
        Element element = doc.getElementById(id);
//        element.getElementsByTag("rb").remove();
        return element.text();
    }

    public String getSimpleText() {
        Element element = doc.getElementById(id);
        stringBuilder = new StringBuilder(100);
        fin = false;
        imgSrc = "";
        while (!fin) {
            element.traverse(new NodeVisitor() {
                public void head(Node node, int depth) {
                    if (fin || suppress) {
                        //
                    } else if (node instanceof TextNode) {
                        if (!((TextNode) node).text().trim().isEmpty()) {
                            if (stringBuilder.length() > 0) stringBuilder.append(" ");
                            stringBuilder.append(((TextNode) node).text().trim());
                        }
                    } else if (node instanceof Element) {
                        if (((Element) node).tagName().equals("img")) {
                            imgSrc = node.attr("src");
                        }
                        String tmpid = node.attr("id");
                        if (!tmpid.isEmpty() && !tmpid.equals(id)) {
                            fin = true;
                        }
                        if (((Element) node).tagName().equalsIgnoreCase("rb") || ((Element) node).tagName().equalsIgnoreCase("rp")) {
                            suppress = true;
                        }
                    }
                }

                public void tail(Node node, int depth) {
                    if (node instanceof Element) {
                        if (((Element) node).tagName().equalsIgnoreCase("rb") || ((Element) node).tagName().equalsIgnoreCase("rp")) {
                            suppress = false;
                        }
                    }
                }
            });
            if (!fin) {
                element = element.nextElementSibling();
                if (element == null) {
                    fin = true;
                }
            }
        }
        return stringBuilder.toString();
    }

    //TODO performance issue
    public List<String> getPartText(List<String> idList) {
        Element element = doc.getElementById(id);
        stringBuilder = new StringBuilder(100);
        List<String> list = new ArrayList<>();
        fin = false;
        imgSrc = "";
        while (!fin) {
            element.traverse(new NodeVisitor() {
                public void head(Node node, int depth) {
                    if (fin || suppress) {
                        //
                    } else if (node instanceof TextNode) {
                        if (!((TextNode) node).text().trim().isEmpty()) {
                            if (stringBuilder.length() > 0) stringBuilder.append(" ");
                            stringBuilder.append(((TextNode) node).text().trim());
                        }
                    } else if (node instanceof Element) {
                        if (((Element) node).tagName().equals("img")) {
                            imgSrc = node.attr("src");
                        }
                        String tmpid = node.attr("id");
                        if (!tmpid.isEmpty() && !tmpid.equals(id)) {
                            if (!idList.contains(tmpid)) {
                                fin = true;
                            } else {
                                list.add(stringBuilder.toString());
                                stringBuilder = new StringBuilder(100);
                            }
                        }
                        if (((Element) node).tagName().equalsIgnoreCase("rb") || ((Element) node).tagName().equalsIgnoreCase("rp")) {
                            suppress = true;
                        }
                    }
                }

                public void tail(Node node, int depth) {
                    if (node instanceof Element) {
                        if (((Element) node).tagName().equalsIgnoreCase("rb") || ((Element) node).tagName().equalsIgnoreCase("rp")) {
                            suppress = false;
                        }
                    }
                }
            });
            if (!fin) {
                element = element.nextElementSibling();
                if (element == null) {
                    fin = true;
                }
            }
        }
        list.add(stringBuilder.toString());
        return list;
    }

    /**
     * Retrieve img src from specified id range
     * img src available after call getText() method
     *
     * @return string result
     */
    public String getImgSrc() {
        return imgSrc;
    }

    public String getImg() {
        Element element = doc.getElementsByTag("img").first();
        return element.attr("src");
    }

    @Override
    public boolean hasText() {
        final Element element = doc.getElementById(id);
        if (element == null || element.text() == null) {
            return false;
        } else {
            return true;
        }
    }

    public String getId() {
        // TODO 20120214 (jharty): Consider keeping the composite reference as
        // the ID since these IDs are only truly unique in the context of the
        // filename...
        return id;
    }

}
