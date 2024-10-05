package org.androiddaisyreader.model;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import junit.framework.TestCase;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for XmlUtilities class which was lacking tests until now.
 * 
 * I want to improve that class by writing suitable tests.
 * 
 * @author Julian Harty
 *
 */
public class XmlUtilitiesTest extends TestCase {

	private static final String XMLFIRSTLINE_UTF8 =
			"<?xml version='1.0' encoding='utf-8'?><html></html>";
	private static final String XMLFIRSTLINE_SJIS =
			"<?xml version='1.0' encoding='shift_jis'?><html></html>";
	private static final String XMLFIRSTLINE_UTF8R =
			"<?xml version='1.0' encoding='utf-8'    ?><html></html>";

	public void testResettableInputStreamReturnsEncoding() throws IOException {
		try {
			@SuppressWarnings("unused")
			String dontCare = XmlUtilities.obtainEncodingStringFromInputStream(null);
		} catch (NullPointerException npe) {
			// pass
		}
	}

	public void testCorrectExceptionThrownWhenInappropriateInputStreamUsed() throws IOException {
		// Create a local instance of InputStream that doesn't support mark or reset.
		InputStream bis = new InputStream() {

			@Override
			public int read() throws IOException {
				return 0;
			}

		};
		try {
			@SuppressWarnings("unused")
			String dontCare = XmlUtilities.obtainEncodingStringFromInputStream(bis);
			fail ("Expected an IllegalArgumentException to be thrown.");
		} catch (IllegalArgumentException iae) {
			// pass
		}
	}

	public void testParsingOfSimpleSmil10WithText() throws IOException, SAXException, ParserConfigurationException {
		InputStream contents = new ByteArrayInputStream(XMLFIRSTLINE_UTF8.getBytes(Charset.forName("UTF-8")));
		contents = XmlUtilities.replaceXmlEncodingString(new BufferedInputStream(contents));
		assertEquals(XMLFIRSTLINE_UTF8, new String(contents.readAllBytes()));

		contents = new ByteArrayInputStream(XMLFIRSTLINE_SJIS.getBytes(Charset.forName("Shift_JIS")));
		contents = XmlUtilities.replaceXmlEncodingString(new BufferedInputStream(contents));
		assertEquals(XMLFIRSTLINE_UTF8R, new String(contents.readAllBytes()));
	}
}
