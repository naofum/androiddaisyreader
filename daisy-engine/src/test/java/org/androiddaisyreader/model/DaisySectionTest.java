package org.androiddaisyreader.model;

import junit.framework.TestCase;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@SuppressWarnings("deprecation")
public class DaisySectionTest extends TestCase {

    @Override
    protected void setUp() {
    }

    public void testDaysy202Section1() throws IOException {
        String bookPath = "./sdcard/files-used-for-testing/testfiles/daisy202/gonM.zip";
        BookContext context = new ZippedBookContext(bookPath);
        DaisyBook thingy = NccSpecification.readFromStream(new BufferedInputStream(context.getResource("ncc.html")));
        assertEquals("ごん狐", thingy.getTitle());

        DaisySection section = new DaisySection.Builder().setHref("njpu0001.smil#njpu_0001")
                .setContext(context).build();
        assertEquals("njpu0001.smil#njpu_0001", section.getHref());

        Part[] parts = section.getParts(true, bookPath);
        assertEquals(3, parts.length);

        assertEquals(1, parts[0].getSnippets().size());
        assertEquals("njpu_0001", parts[0].getSnippets().get(0).getId());
        assertEquals("ごん狐", parts[0].getSnippets().get(0).getText());

        assertEquals(1, parts[1].getSnippets().size());
        assertEquals("xnjp_0001", parts[1].getSnippets().get(0).getId());
        assertEquals("新美南吉", parts[1].getSnippets().get(0).getText());

        assertEquals(1, parts[2].getSnippets().size());
        assertEquals("njpu_0003", parts[2].getSnippets().get(0).getId());
        assertEquals("1", parts[2].getSnippets().get(0).getText());

        section = new DaisySection.Builder().setHref("njpu0002.smil#njpu_0004")
                .setContext(context).build();
        assertEquals("njpu0002.smil#njpu_0004", section.getHref());

        parts = section.getParts(true, bookPath);
        assertEquals(41, parts.length);
        assertEquals(null, parts[0].getId());

        List<Snippet> snipetts = parts[0].getSnippets();
        List<Audio> audio = parts[0].getAudioElements();
        List<Navigable> navigables = parts[0].getChildren();
        assertEquals(1, snipetts.size());
        assertTrue(parts[0].hasAudio());
//        assertTrue(parts[0].hasImage());
        assertTrue(parts[0].hasSnippets());

        assertEquals(1, snipetts.size());
        assertEquals("njpu_0004", snipetts.get(0).getId());
        assertEquals("一", snipetts.get(0).getText());
        assertEquals(1, audio.size());
        assertEquals("qwrt_0001", audio.get(0).getId());
        assertEquals("2.mp3", audio.get(0).getAudioFilename());
        assertEquals(0, audio.get(0).getClipBegin());
        assertEquals(1583, audio.get(0).getClipEnd());
        assertNull(navigables);

        snipetts = parts[1].getSnippets();
        assertEquals(1, snipetts.size());
        assertEquals("xnjp_0002", snipetts.get(0).getId());
//        assertEquals("これは、 私 （ わたし ） が小さいときに、村の 茂平 （ もへい ） というおじいさんからきいたお話です。", snipetts.get(0).getText());
        assertEquals("これは、 わたし が小さいときに、村の もへい というおじいさんからきいたお話です。", snipetts.get(0).getText());
    }

    public void testDaysy202Section() throws IOException {
        String bookPath = "./sdcard/files-used-for-testing/testfiles/daisy202/pigs.zip";
        BookContext context = new ZippedBookContext(bookPath);
        DaisyBook thingy = NccSpecification.readFromStream(new BufferedInputStream(context.getResource("ncc.html")));
        assertEquals("三匹の子ぶた", thingy.getTitle());

        DaisySection section = new DaisySection.Builder().setHref("mrii0001.smil#mrii_0001")
                .setContext(context).build();
        assertEquals("mrii0001.smil#mrii_0001", section.getHref());

        Part[] parts = section.getParts(true, bookPath);
        assertEquals(7, parts.length);
        assertEquals(null, parts[0].getId());

        List<Snippet> snipetts = parts[0].getSnippets();
        List<Audio> audio = parts[0].getAudioElements();
        List<Navigable> navigables = parts[0].getChildren();
        assertEquals(1, snipetts.size());
        assertTrue(parts[0].hasAudio());
//        assertTrue(parts[0].hasImage());
        assertTrue(parts[0].hasSnippets());

        assertEquals(1, snipetts.size());
        assertEquals("mrii_0001", snipetts.get(0).getId());
        assertEquals("三匹の子ぶた", snipetts.get(0).getText());
        assertEquals(1, audio.size());
        assertEquals("qwrt_0001", audio.get(0).getId());
        assertEquals("1.mp3", audio.get(0).getAudioFilename());
        assertEquals(0, audio.get(0).getClipBegin());
        assertEquals(2713, audio.get(0).getClipEnd());
        assertNull(navigables);

        snipetts = parts[1].getSnippets();
        assertEquals(1, snipetts.size());
        assertEquals("xmri_0001", snipetts.get(0).getId());
        assertEquals("ジョウジフ・ジェーコブス 作", snipetts.get(0).getText());

        snipetts = parts[2].getSnippets();
        assertEquals(1, snipetts.size());
        assertEquals("xmri_0002", snipetts.get(0).getId());
        assertEquals("ぶたが詩を歌っているころ", snipetts.get(0).getText());

        snipetts = parts[3].getSnippets();
        assertEquals(1, snipetts.size());
        assertEquals("xmri_0003", snipetts.get(0).getId());
        assertEquals("さるはタバコを噛んでいた", snipetts.get(0).getText());

        snipetts = parts[4].getSnippets();
        assertEquals(1, snipetts.size());
        assertEquals("xmri_0004", snipetts.get(0).getId());

        snipetts = parts[5].getSnippets();
        assertEquals(1, snipetts.size());
        assertEquals("xmri_0005", snipetts.get(0).getId());

        snipetts = parts[6].getSnippets();
        assertEquals(1, snipetts.size());
        assertEquals("mrii_0003", snipetts.get(0).getId());

        section = new DaisySection.Builder().setHref("mrii0002.smil#mrii_0002")
                .setContext(context).build();
        assertEquals("mrii0002.smil#mrii_0002", section.getHref());

        parts = section.getParts(true, "mrii0002.smil");
        assertEquals(16, parts.length);

        snipetts = parts[0].getSnippets();
        assertEquals(1, snipetts.size());
        assertEquals("mrii_0004", snipetts.get(0).getId());
        assertEquals("01", snipetts.get(0).getText());

        snipetts = parts[1].getSnippets();
        assertEquals(1, snipetts.size());
        assertEquals("xmri_0006", snipetts.get(0).getId());
        assertEquals("昔々、三匹の子ぶたを産んだ母ぶたがいました。", snipetts.get(0).getText());

    }

    public void testDaysy3Section() throws IOException {
        String bookPath = "./sdcard/files-used-for-testing/testfiles/daisy3/are-you-ready-z3986.zip";
        BookContext context = new ZippedBookContext(bookPath);
        InputStream contents = context.getResource("speechgen.opf");
        DaisyBook thingy = OpfSpecification.readFromStream(new BufferedInputStream(contents), context);
        assertEquals("ARE YOU READY?", thingy.getTitle());

        DaisySection section = new DaisySection.Builder().setHref("speechgen0001.smil#tcp1")
                .setContext(context).build();
        assertEquals("speechgen0001.smil#tcp1", section.getHref());

        Part[] parts = section.getParts(false, bookPath);
        assertEquals(2, parts.length);
        assertEquals("tcp1", parts[0].getId());

        List<Snippet> snipetts = parts[0].getSnippets();
        List<Audio> audio = parts[0].getAudioElements();
        List<Navigable> navigables = parts[0].getChildren();
        assertEquals(1, snipetts.size());
        assertTrue(parts[0].hasAudio());
//        assertTrue(parts[0].hasImage());
        assertTrue(parts[0].hasSnippets());

        assertEquals(1, snipetts.size());
        assertEquals("dtb1", snipetts.get(0).getId());
        assertEquals("ARE YOU READY?", snipetts.get(0).getText());
        assertEquals(1, audio.size());
        assertEquals(null, audio.get(0).getId());
        assertEquals("speechgen0001.mp3", audio.get(0).getAudioFilename());
        assertEquals(0, audio.get(0).getClipBegin());
        assertEquals(2029, audio.get(0).getClipEnd());
        assertNull(navigables);
    }

    public void testEpub2Section() throws IOException {
        String bookPath = "./sdcard/files-used-for-testing/testfiles/miniepub3/valentin-hauy.epub";
        BookContext context = new SimpleBookContext(bookPath);
        InputStream contents = context.getResource("package.opf");
        DaisyBook thingy = Opf31Specification.readFromStream(new BufferedInputStream(contents), context);
        assertEquals("Valentin Haüy - the father of the education for the blind", thingy.getTitle());

        DaisySection section = new DaisySection.Builder().setHref("valentinhauy11.html#opf1")
                .setContext(context).build();
        assertEquals("valentinhauy11.html#opf1", section.getHref());

        Part[] parts = section.getParts(false, bookPath);
        assertEquals(28, parts.length);

        List<Snippet> snipetts = parts[0].getSnippets();
        List<Audio> audio = parts[0].getAudioElements();
        List<Navigable> navigables = parts[0].getChildren();
        assertEquals("ops1", parts[0].getId());

        assertEquals("ops1", snipetts.get(0).getId());
        assertEquals("Valentin Haüy The father of the education for the blind " +
                "by Beatrice Christensen-Sköld " +
                "Published by the Swedish Library of Talking Books and Braille (TPB). " +
                "Beatrice Christensen Sköld " +
                "Valentin Haüy – the Father of the Education for the Blind " +
                "The Swedish Library of Talking Books and Braille (TPB)", snipetts.get(0).getText());

        assertEquals("rgn_cnt_0005", snipetts.get(1).getId());
        assertEquals("In this study the life and works of Valentin Haüy are described.", snipetts.get(1).getText());

        assertEquals("rgn_cnt_0006", snipetts.get(2).getId());
        assertEquals("Valentin Haüy (1745-1821), of many called the father of the education of the blind,", snipetts.get(2).getText());
    }

    public void testAEpub3Section() throws IOException {
        String bookPath = "./sdcard/files-used-for-testing/testfiles/miniepub3/accessible_epub_3.epub";
        BookContext context = new SimpleBookContext(bookPath);
        InputStream contents = context.getResource("package.opf");
        DaisyBook thingy = Opf31Specification.readFromStream(new BufferedInputStream(contents), context);
        assertEquals("Accessible EPUB 3", thingy.getTitle());

        DaisySection section = (DaisySection) thingy.sections.get(0);

        Part[] parts = section.getParts(false, bookPath);
        assertEquals(1, parts.length);

        List<Snippet> snipetts = parts[0].getSnippets();
        List<Audio> audio = parts[0].getAudioElements();
        List<Navigable> navigables = parts[0].getChildren();
        assertEquals("I_book_d1e1", parts[0].getId());

        assertEquals("I_book_d1e1", snipetts.get(0).getId());
        assertEquals("Accessible EPUB 3 Matt Garrish Editor Brian Sawyer Editor Dan Fauxsmith Copyright © 2012 O’Reilly Media, Inc", snipetts.get(0).getText());
    }

}
