package org.androiddaisyreader.model;

import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * EPUB3メディアオーバーレイ（index.xhtml + smil/index.smil + mp3、章なし）が
 * セクションとして取得できることを検証するリグレッションテスト。
 *
 * <p>index.xhtml の &lt;h1&gt; 見出しに id が無い場合でも、Opf31Specification が
 * id=null のモデルをスキップして後続の span をセクションにすることを検証する。</p>
 */
public class Epub3MediaOverlaySectionTest {

    @Test
    public void mediaOverlayEpubCreatesSection() throws Exception {
        Path epub = newTempEpub();
        writeEpub(epub, true);

        assertEpub(epub, "smil/index.smil#title");
    }

    @Test
    public void mediaOverlayEpubWithoutHeadingIdCreatesSection() throws Exception {
        Path epub = newTempEpub();
        writeEpub(epub, false);

        assertEpub(epub, "smil/index.smil#s001_0001");
    }

    @Test
    public void multiChapterEpubCreatesSectionPerChapter() throws Exception {
        Path epub = newTempEpub();
        writeMultiChapterEpub(epub);

        ZippedBookContext context = new ZippedBookContext(epub.toString());
        InputStream opf = context.getResource("content.opf");
        assertNotNull(opf);

        DaisyBook book = Opf31Specification.readFromStream(new BufferedInputStream(opf), context);
        assertEquals(2, book.sections.size());
        assertEquals(2, book.getChildren().size());

        DaisySection first = (DaisySection) book.getChildren().get(0);
        assertEquals("smil/chapter_0001.smil#s001_0001", first.getHref());
        Part[] firstParts = first.getParts(false, epub.toString());
        assertEquals(1, firstParts.length);
        assertEquals("../audio/audio_0001.mp3",
                firstParts[0].getAudioElements().get(0).getAudioFilename());

        DaisySection second = (DaisySection) book.getChildren().get(1);
        assertEquals("smil/chapter_0002.smil#s002_0001", second.getHref());
        Part[] secondParts = second.getParts(false, epub.toString());
        assertEquals(1, secondParts.length);
        assertEquals("../audio/audio_0002.mp3",
                secondParts[0].getAudioElements().get(0).getAudioFilename());
    }

    private Path newTempEpub() throws Exception {
        Path epub = Files.createTempFile("voice", ".epub");
        Files.deleteIfExists(epub);
        // WindowsではZippedBookContextがZipFileを掴んだままになり削除できないため、
        // 削除はJVM終了時に委ねる。
        epub.toFile().deleteOnExit();
        return epub;
    }

    private void assertEpub(Path epub, String expectedHref) throws Exception {
        ZippedBookContext context = new ZippedBookContext(epub.toString());
        InputStream opf = context.getResource("content.opf");
        assertNotNull(opf);

        DaisyBook book = Opf31Specification.readFromStream(new BufferedInputStream(opf), context);
        assertEquals(1, book.sections.size());
        assertEquals(1, book.getChildren().size());

        DaisySection section = (DaisySection) book.getChildren().get(0);
        assertEquals(expectedHref, section.getHref());

        Part[] parts = section.getParts(false, epub.toString());
        assertEquals(2, parts.length);
        assertEquals("../audio/audio_0001.mp3", parts[0].getAudioElements().get(0).getAudioFilename());
        assertEquals("../audio/audio_0002.mp3", parts[1].getAudioElements().get(0).getAudioFilename());
    }

    private void writeEpub(Path epub, boolean withHeadingId) throws Exception {
        String heading = withHeadingId
                ? "<h1 id=\"title\">音声版広報</h1>\n"
                : "<h1>音声版広報</h1>\n";

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(epub.toFile()))) {
            addEntry(zip, "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
            addEntry(zip, "META-INF/container.xml",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
                            + "  <rootfiles>\n"
                            + "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n"
                            + "  </rootfiles>\n"
                            + "</container>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/content.opf",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<package version=\"3.0\" xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"uid\">\n"
                            + "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
                            + "    <dc:identifier id=\"uid\">test</dc:identifier>\n"
                            + "    <dc:title>音声版広報</dc:title>\n"
                            + "    <dc:language>ja</dc:language>\n"
                            + "  </metadata>\n"
                            + "  <manifest>\n"
                            + "    <item id=\"index\" href=\"index.xhtml\" media-type=\"application/xhtml+xml\" media-overlay=\"smil_1\"/>\n"
                            + "    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n"
                            + "    <item id=\"smil_1\" href=\"smil/index.smil\" media-type=\"application/smil+xml\"/>\n"
                            + "    <item id=\"audio_1\" href=\"audio/audio_0001.mp3\" media-type=\"audio/mpeg\"/>\n"
                            + "    <item id=\"audio_2\" href=\"audio/audio_0002.mp3\" media-type=\"audio/mpeg\"/>\n"
                            + "  </manifest>\n"
                            + "  <spine>\n"
                            + "    <itemref idref=\"index\"/>\n"
                            + "  </spine>\n"
                            + "</package>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/index.xhtml",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"ja\" lang=\"ja\">\n"
                            + "<head><title>音声版広報</title></head>\n"
                            + "<body>\n"
                            + heading
                            + "<p><span id=\"s001_0001\">1面</span></p>\n"
                            + "<p><span id=\"s002_0001\">2面</span></p>\n"
                            + "</body>\n</html>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/nav.xhtml",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"ja\" lang=\"ja\">\n"
                            + "<head><title>目次</title></head>\n"
                            + "<body><nav epub:type=\"toc\"><ol><li><a href=\"index.xhtml#s001_0001\">1面</a></li></ol></nav></body>\n"
                            + "</html>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/smil/index.smil",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<smil xmlns=\"http://www.w3.org/ns/SMIL\" version=\"3.0\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n"
                            + "<body><seq id=\"seq_1\" epub:textref=\"../index.xhtml\">\n"
                            + "<par id=\"par_1\"><text src=\"../index.xhtml#s001_0001\"/><audio src=\"../audio/audio_0001.mp3\"/></par>\n"
                            + "<par id=\"par_2\"><text src=\"../index.xhtml#s002_0001\"/><audio src=\"../audio/audio_0002.mp3\"/></par>\n"
                            + "</seq></body></smil>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/audio/audio_0001.mp3", new byte[]{0x49, 0x44, 0x33});
            addEntry(zip, "OEBPS/audio/audio_0002.mp3", new byte[]{0x49, 0x44, 0x33});
        }
    }

    private void addEntry(ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private void writeMultiChapterEpub(Path epub) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(epub.toFile()))) {
            addEntry(zip, "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
            addEntry(zip, "META-INF/container.xml",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
                            + "  <rootfiles>\n"
                            + "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n"
                            + "  </rootfiles>\n"
                            + "</container>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/content.opf",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<package version=\"3.0\" xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"uid\">\n"
                            + "  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n"
                            + "    <dc:identifier id=\"uid\">test</dc:identifier>\n"
                            + "    <dc:title>音声版広報</dc:title>\n"
                            + "    <dc:language>ja</dc:language>\n"
                            + "  </metadata>\n"
                            + "  <manifest>\n"
                            + "    <item id=\"chapter_0001\" href=\"chapter_0001.xhtml\" media-type=\"application/xhtml+xml\" media-overlay=\"smil_0001\"/>\n"
                            + "    <item id=\"chapter_0002\" href=\"chapter_0002.xhtml\" media-type=\"application/xhtml+xml\" media-overlay=\"smil_0002\"/>\n"
                            + "    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n"
                            + "    <item id=\"smil_0001\" href=\"smil/chapter_0001.smil\" media-type=\"application/smil+xml\"/>\n"
                            + "    <item id=\"smil_0002\" href=\"smil/chapter_0002.smil\" media-type=\"application/smil+xml\"/>\n"
                            + "    <item id=\"audio_1\" href=\"audio/audio_0001.mp3\" media-type=\"audio/mpeg\"/>\n"
                            + "    <item id=\"audio_2\" href=\"audio/audio_0002.mp3\" media-type=\"audio/mpeg\"/>\n"
                            + "  </manifest>\n"
                            + "  <spine>\n"
                            + "    <itemref idref=\"chapter_0001\"/>\n"
                            + "    <itemref idref=\"chapter_0002\"/>\n"
                            + "  </spine>\n"
                            + "</package>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/chapter_0001.xhtml",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"ja\" lang=\"ja\">\n"
                            + "<head><title>1面</title></head>\n"
                            + "<body>\n"
                            + "<h2>1面</h2>\n"
                            + "<p><span id=\"s001_0001\">1面</span></p>\n"
                            + "</body>\n</html>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/chapter_0002.xhtml",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"ja\" lang=\"ja\">\n"
                            + "<head><title>2面</title></head>\n"
                            + "<body>\n"
                            + "<h2>2面</h2>\n"
                            + "<p><span id=\"s002_0001\">2面</span></p>\n"
                            + "</body>\n</html>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/nav.xhtml",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"ja\" lang=\"ja\">\n"
                            + "<head><title>目次</title></head>\n"
                            + "<body><nav epub:type=\"toc\"><ol>"
                            + "<li><a href=\"chapter_0001.xhtml\">1面</a></li>"
                            + "<li><a href=\"chapter_0002.xhtml\">2面</a></li>"
                            + "</ol></nav></body>\n</html>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/smil/chapter_0001.smil",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<smil xmlns=\"http://www.w3.org/ns/SMIL\" version=\"3.0\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n"
                            + "<body><seq id=\"seq_1\" epub:textref=\"../chapter_0001.xhtml\">\n"
                            + "<par id=\"par_1\"><text src=\"../chapter_0001.xhtml#s001_0001\"/><audio src=\"../audio/audio_0001.mp3\"/></par>\n"
                            + "</seq></body></smil>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/smil/chapter_0002.smil",
                    ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<smil xmlns=\"http://www.w3.org/ns/SMIL\" version=\"3.0\" xmlns:epub=\"http://www.idpf.org/2007/ops\">\n"
                            + "<body><seq id=\"seq_1\" epub:textref=\"../chapter_0002.xhtml\">\n"
                            + "<par id=\"par_1\"><text src=\"../chapter_0002.xhtml#s002_0001\"/><audio src=\"../audio/audio_0002.mp3\"/></par>\n"
                            + "</seq></body></smil>\n").getBytes(StandardCharsets.UTF_8));
            addEntry(zip, "OEBPS/audio/audio_0001.mp3", new byte[]{0x49, 0x44, 0x33});
            addEntry(zip, "OEBPS/audio/audio_0002.mp3", new byte[]{0x49, 0x44, 0x33});
        }
    }
}
