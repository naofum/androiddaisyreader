/**
 * Extract the XML encoding from XML content, to help parse content correctly.
 */
package org.androiddaisyreader.model;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.StringReader;
import java.util.regex.Pattern;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 * Simple Utility class to extract the XML encoding from a text file.
 * 
 * @author Julian Harty
 */
public final class XmlUtilities {
    private static final int ENOUGH = 200;
    protected static final String XML_TRAILER = "\"?>";
    protected static final String EXTRACT_ENCODING_REGEX = ".*encoding=\"";
    protected static final String XML_FIRST_LINE_REGEX = "<\\?xml version=\"1\\.0\" encoding=\"(.*)\"?>";
    protected static final int FIRST_LINE_SIZE = 45;

    // Hide the constructor for this utility class.
    private XmlUtilities() {
    };

    /**
     * Helper method to extract the XML file encoding
     * 
     * @param line the first line of an XML file
     * @return The value of the encoding in lower-case.
     */
    protected static String extractEncoding(String line) {
        Pattern p = Pattern.compile(EXTRACT_ENCODING_REGEX);
        String matches[] = p.split(line);
        // We want the value after encoding="
        String value = matches[1];
        // We don't need anything after the first " after the value
        String cleanup[] = value.split("\"");
        String encoding = cleanup[0];
        return encoding.toLowerCase();
    }

    /**
     * Helper method to map an unsupported XML encoding to a similar encoding.
     * 
     * Currently limited to processing windows-1252 encoding.
     * 
     * @param encoding The encoding string e.g. "windows-1252"
     * @return a similar, hopefully supported encoding, where we have a suitable
     *         match, else the original encoding.
     */
    public static String mapUnsupportedEncoding(String encoding) {
        if (encoding.equalsIgnoreCase("windows-1252")) {
            return "iso-8859-1";
        }
        return encoding;
    }

    /**
     * Helper method to obtain the content encoding from an input stream.
     * 
     * @param bis file to parse
     * @return the encoding if we are able to extract and parse it, else the
     *         default value expected by the expat parser, i.e. "UTF-8"
     * @throws {@link IOException} if there is a problem reading from the file.
     * @throws {@link IllegalArgumentException} if the InputStream does not
     *         support mark and reset.
     */
    public static String obtainEncodingStringFromInputStream(InputStream bis) throws IOException {
        String encoding = "UTF-8";
        if (!bis.markSupported()) {
            throw new IllegalArgumentException(
                    "Error in the program, InputStream needs to support markSupported()");
        }

        // read the first line after setting the mark, then reset
        // before calling the parser.
        bis.mark(ENOUGH);
        DataInputStream dis = new DataInputStream(bis);

        String line = dis.readLine();
        line = line.replace("'", "\"");
        if (line.matches(XML_FIRST_LINE_REGEX)) {
            encoding = extractEncoding(line);
        }
        bis.reset();
//        dis.close();
        return encoding;
    }

    /**
     * Provide a dummy XML resolver.
     * 
     * @return the dummy resolver.
     */
    public static EntityResolver dummyEntityResolver() {
        // Thanks to http://www.junlu.com/msg/202604.html
        EntityResolver er = new EntityResolver() {
            public InputSource resolveEntity(String publicId, String systemId) {
                return new InputSource(new StringReader(" "));
            }
        };
        return er;
    }

    /**
     * Replace xml first line encoding string 'shift_jis' to 'utf-8'
     * Android default SAX parser not support shift_jis encoding
     *
     * @return inputStream
     */
    public static InputStream replaceXmlEncodingString(InputStream bis) throws IOException {
        byte[] line = new byte[FIRST_LINE_SIZE];
        PushbackInputStream pushbackStream = new PushbackInputStream(bis, FIRST_LINE_SIZE);
        if (!bis.markSupported()) {
            throw new IllegalArgumentException(
                    "Error in the program, InputStream needs to support markSupported()");
        }
        int n = pushbackStream.read(line, 0, line.length);
        if (n > 41) {
            for (int i = 31; i < 35; i++) {
                if (line[i] == 'h' && line[i + 1] == 'i' && line[i + 2] == 'f' && line[i + 3] == 't') {
                    line[i - 1] = 'u';
                    line[i] = 't';
                    line[i + 1] = 'f';
                    line[i + 2] = '-';
                    line[i + 3] = '8';
                    line[i + 4] = line[i - 2];
                    line[i + 5] = ' ';
                    line[i + 6] = ' ';
                    line[i + 7] = ' ';
                    line[i + 8] = ' ';
                    break;
                }
            }
        }
        pushbackStream.unread(line);
        return pushbackStream;
    }

    /**
     * Convert xml encoding
     * Android default SAX parser not support shift_jis encoding
     *
     * @return inputStream
     */
    @Deprecated
    public static InputStream convertEncoding(InputStream contents, String encoding)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        if (encoding.equalsIgnoreCase("shift_jis")) {
            BufferedReader in = null;
            try {
                in = new BufferedReader(new InputStreamReader(contents, "Shift_JIS"));
                while ((line = in.readLine()) != null) {
                    line = line.replaceAll("=\"shift_jis\"", "=\"utf-8\"");
                    line = line.replaceAll("=\"Shift_JIS\"", "=\"utf-8\"");
                    sb.append(line);
                }
//                in.close();
            } catch (IOException e) {
                throw new IOException("Couldn't convert the ncc.html contents to utf-8.", e);
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException e) {
                    //
                }
            }
            return new ByteArrayInputStream(sb.toString().getBytes("utf-8"));
        }
        return contents;
    }
}
