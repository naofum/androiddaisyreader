package org.androiddaisyreader.model;

import junit.framework.TestCase;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import javax.xml.parsers.ParserConfigurationException;

public class DaisySnippetTest extends TestCase {

	private static final String SNIPPET_SOURCE =
		"<h1 class=\"title\" id=\"ops1\">Valentin Haüy The father of the education for the blind</h1>\n" +
		"<p class=\"front\">by Beatrice Christensen-Sköld</p>\n" +
		"<p class=\"front\">Published by the Swedish Library of Talking Books and Braille (TPB).</p>\n" +
		"<p>\n" +
		"Beatrice Christensen Sköld<br />\n" +
		"Valentin Haüy – the Father of the Education for the Blind<br />\n" +
		"The Swedish Library of Talking Books and Braille (TPB)\n" +
		"</p>";

	public void testDaisySnippet1() throws IOException, SAXException, ParserConfigurationException {
		String encoding = "UTF-8";
		InputStream contents = new ByteArrayInputStream(SNIPPET_SOURCE.getBytes(Charset.forName(encoding)));
		Document doc = Jsoup.parse(contents, encoding, "");
		DaisySnippet snippet = new DaisySnippet(doc, "ops1");

		assertTrue(doc.hasText());
		//TODO strict getText instead of getElementById
		assertEquals("Valentin Haüy The father of the education for the blind", snippet.getText());
//		assertEquals("Valentin Haüy The father of the education for the blind " +
//				"by Beatrice Christensen-Sköld " +
//				"Published by the Swedish Library of Talking Books and Braille (TPB). " +
//				"Beatrice Christensen Sköld " +
//				"Valentin Haüy – the Father of the Education for the Blind " +
//				"The Swedish Library of Talking Books and Braille (TPB)", snippet.getText());
	}

	public void testDaisySnippet2() throws IOException, ParserConfigurationException {
		String bookPath = "./sdcard/files-used-for-testing/testfiles/miniepub3/valentin-hauy.epub";
		BookContext context = new SimpleBookContext(bookPath);
		DaisySnippet snippet = new DaisySnippet(context, "valentinhauy11.html#ops1");

		assertTrue(snippet.hasText());
		String testStr1 = "Valentin Haüy The father of the education for the blind";
//		String testStr1 = "Valentin Haüy The father of the education for the blind " +
//				"by Beatrice Christensen-Sköld " +
//				"Published by the Swedish Library of Talking Books and Braille (TPB). " +
//				"Beatrice Christensen Sköld " +
//				"Valentin Haüy – the Father of the Education for the Blind " +
//				"The Swedish Library of Talking Books and Braille (TPB)";
		assertEquals(testStr1, snippet.getText());
	}

	public void testDaisySnippet3() throws IOException, ParserConfigurationException {
		String bookPath = "./sdcard/files-used-for-testing/testfiles/miniepub3/kusamakura.epub";
		BookContext context = new SimpleBookContext(bookPath);
		DaisySnippet snippet = new DaisySnippet(context, "一.xhtml#fgyq_0001");

		assertTrue(snippet.hasText());
		String testStr1 = "一";
		assertEquals(testStr1, snippet.getText());
	}

	public void testGetEpubType() {
		String source = "<span id=\"pg1\" epub:type=\"pagebreak\">3</span>";
		Document doc = Jsoup.parse(source, "");
		DaisySnippet snippet = new DaisySnippet(doc, "pg1");

		assertEquals("pagebreak", snippet.getEpubType());
	}

	public void testGetEpubTypeFromAncestor() {
		String source = "<aside epub:type=\"footnote\"><span id=\"fn1\">note text</span></aside>";
		Document doc = Jsoup.parse(source, "");
		DaisySnippet snippet = new DaisySnippet(doc, "fn1");

		assertEquals("footnote", snippet.getEpubType());
	}

	public void testGetEpubTypeNone() {
		String source = "<span id=\"plain\">plain text</span>";
		Document doc = Jsoup.parse(source, "");
		DaisySnippet snippet = new DaisySnippet(doc, "plain");

		assertNull(snippet.getEpubType());
	}

}
