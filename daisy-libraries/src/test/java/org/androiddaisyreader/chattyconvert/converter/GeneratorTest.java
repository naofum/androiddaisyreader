package org.androiddaisyreader.chattyconvert.converter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorTest {

    private Chapter parseChapter(String html, int index, String title) {
        Document doc = Jsoup.parse(html);
        Element section = doc.selectFirst("section");
        return new Chapter(index, section.id(), title, section);
    }

    private String sampleSectionHtml() {
        return "<html><body>"
                + "<section id=\"section_1_section0001_xhtml\">"
                + "<div id=\"pid_00001\"><h1><span id=\"s001_00001\" class=\"read\">"
                + "<ruby>銀河<rp>(</rp><rt>ぎんが</rt><rp>)</rp></ruby></span></h1></div>"
                + "<p id=\"pid_00002\"><span id=\"s001_00002\" class=\"read\">テキスト</span>"
                + "<span class=\"infty_silent\">」</span></p>"
                + "</section>"
                + "</body></html>";
    }

    @Test
    void testXhtmlGenerator() {
        Chapter chapter = parseChapter(sampleSectionHtml(), 1, "銀河鉄道の夜");
        XhtmlGenerator generator = new XhtmlGenerator();

        String result = generator.generate(chapter, Arrays.asList("css/style.css", "css/style_v.css"));

        assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(result.contains("<html xmlns=\"http://www.w3.org/1999/xhtml\""));
        assertTrue(result.contains("<title>銀河鉄道の夜</title>"));
        assertTrue(result.contains("<link rel=\"stylesheet\" type=\"text/css\" href=\"css/style.css\"/>"));
        assertTrue(result.contains("id=\"s001_00001\""));
        assertTrue(result.contains("<ruby>銀河<rp>(</rp><rt>ぎんが</rt><rp>)</rp></ruby>"));
        assertTrue(result.contains("class=\"infty_silent\""));
    }

    @Test
    void testXhtmlGenerator_escapesTitle() {
        Chapter chapter = parseChapter(sampleSectionHtml(), 1, "A <B> & \"C\"");
        XhtmlGenerator generator = new XhtmlGenerator();

        String result = generator.generate(chapter, List.of());

        assertTrue(result.contains("<title>A &lt;B&gt; &amp; &quot;C&quot;</title>"));
    }

    @Test
    void testSmilGenerator_withAudioAndTextOnly() {
        Chapter chapter = parseChapter(sampleSectionHtml(), 1, "銀河鉄道の夜");
        Map<String, AudioItem> audioMap = new HashMap<>();
        audioMap.put("s001_00001",
                new AudioItem("sounds/sound00001.mp3", "0.030s", "2.820s", 2.82));
        audioMap.put("s001_00002",
                new AudioItem("sounds/sound00001.mp3", "2.820s", "6.092s", 6.092));
        SmilGenerator generator = new SmilGenerator();

        String result = generator.generate(chapter, audioMap, "../chapter_0001.xhtml");

        assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(result.contains("<seq id=\"seq_1\" epub:textref=\"../chapter_0001.xhtml\""));
        assertTrue(result.contains("<text src=\"../chapter_0001.xhtml#s001_00001\"/>"));
        assertTrue(result.contains("<audio src=\"../sounds/sound00001.mp3\" clipBegin=\"0.030s\" clipEnd=\"2.820s\"/>"));
        assertTrue(result.contains("<text src=\"../chapter_0001.xhtml#s001_00002\"/>"));
        assertTrue(result.contains("<audio src=\"../sounds/sound00001.mp3\" clipBegin=\"2.820s\" clipEnd=\"6.092s\"/>"));
        assertTrue(result.endsWith("</seq>\n</body>\n</smil>"));
    }

    @Test
    void testSmilGenerator_audioMissingProducesTextOnlyPar() {
        Chapter chapter = parseChapter(sampleSectionHtml(), 1, "題");
        SmilGenerator generator = new SmilGenerator();

        String result = generator.generate(chapter, new HashMap<>(), "../chapter_0001.xhtml");

        assertTrue(result.contains("<text src=\"../chapter_0001.xhtml#s001_00001\"/>"));
        assertTrue(result.contains("<text src=\"../chapter_0001.xhtml#s001_00002\"/>"));
        assertTrue(result.matches("(?s).*<par id=\"par_1\">.*<par id=\"par_2\">.*"));
    }

    @Test
    void testNavGenerator() {
        Chapter c1 = parseChapter(sampleSectionHtml(), 1, "銀河鉄道の夜");
        Chapter c2 = parseChapter(sampleSectionHtml(), 2, "一　午後の授業");
        BookData data = new BookData();
        data.setTitle("銀河鉄道の夜");
        data.addChapter(c1);
        data.addChapter(c2);
        NavGenerator generator = new NavGenerator();

        String result = generator.generate(data, data.getTitle());

        assertTrue(result.contains("<nav epub:type=\"toc\" id=\"toc\">"));
        assertTrue(result.contains("<li><a href=\"chapter_0001.xhtml\">銀河鉄道の夜</a></li>"));
        assertTrue(result.contains("<li><a href=\"chapter_0002.xhtml\">一　午後の授業</a></li>"));
    }

    @Test
    void testFormatDuration() {
        assertEquals("0:00:00.000", EpubAssembler.formatDuration(0.0));
        assertEquals("0:00:02.820", EpubAssembler.formatDuration(2.82));
        assertEquals("1:00:00.000", EpubAssembler.formatDuration(3600.0));
        assertEquals("2:17:56.835", EpubAssembler.formatDuration(2 * 3600 + 17 * 60 + 56.835));
        assertEquals("0:00:59.999", EpubAssembler.formatDuration(59.999));
    }
}
