package org.androiddaisyreader.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
//import org.jsoup.safety.Safelist;
import org.jsoup.safety.Whitelist;

/**
 * FullText represents the contents of a DAISY full-text book.
 * 
 * Next Steps: - Experiment with using jsoup.
 * 
 * @author jharty
 */
public class FullText {

    private Document documentContents;

    /**
     * Simply reads the contents from the HTML File.
     * 
     * @param fileToReadFrom the full filename of the file to read.
     * @return the contents of the file in a StringBuffer.
     * @throws FileNotFoundException
     * @throws IOException
     */
    StringBuilder getContentsOfHTMLFile(File fileToReadFrom) throws FileNotFoundException,
            IOException {
        BufferedReader reader =null;
        StringBuilder fileContents = new StringBuilder();
        try {
            File file = fileToReadFrom;
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                    Charset.forName("UTF-8")));
            String line;

            while ((line = reader.readLine()) != null) {
                fileContents.append(line);
                fileContents.append('\n');
            }

            file = null;
//            reader.close();
//            reader = null;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return fileContents;
    }

    /**
     * Process HTML contained in text and return a Jsoup document.
     * 
     * @param text to process with HTML markup e.g. &lt;b&gt;Hello&lt;/b&gt;
     * @return a JSoup document
     */
    public Document processHTML(String text) {
        documentContents = Jsoup.parse(text);
        return documentContents;
    }

    /**
     * Returns the inner HTML for a given smilReference.
     * 
     * @param smilReference the reference e.g. "id_224"
     */
    public String getHtmlFor(String reference) {
        String contents = documentContents.getElementById(reference).html();
//        return Jsoup.clean(contents, Safelist.simpleText());
        return Jsoup.clean(contents, Whitelist.simpleText());
    }

}
