package org.androiddaisyreader.aozoraconvert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AozoraEpubConverterのユニットテスト。
 */
class AozoraEpubConverterTest {

    @TempDir
    Path tempDir;

    private final AozoraEpubConverter converter = new AozoraEpubConverter();

    @Test
    void testConvert_minimalXhtml() throws Exception {
        File inputDir = prepareMinimalInput();
        File output = tempDir.resolve("output.epub").toFile();

        File result = converter.convert(inputDir, "テスト作品", "テスト著者", output);

        assertNotNull(result);
        assertTrue(result.isFile());
        assertTrue(result.length() > 0);

        // EPUBはZIPフォーマット
        try (ZipFile zip = new ZipFile(result)) {
            assertNotNull(zip.getEntry("mimetype"));
            assertNotNull(zip.getEntry("META-INF/container.xml"));
            assertNotNull(zip.getEntry("OEBPS/content.opf"));
            assertNotNull(zip.getEntry("OEBPS/toc.ncx"));

            // content.opfにメタデータが含まれる
            String opf = readZipEntry(zip, "OEBPS/content.opf");
            assertTrue(opf.contains("テスト作品"), "title should be in OPF");
            assertTrue(opf.contains("テスト著者"), "author should be in OPF");
            assertTrue(opf.contains("青空文庫"), "publisher should be in OPF");
            assertTrue(opf.contains("<dc:language>ja</dc:language>"), "language should be ja");
        }
    }

    @Test
    void testConvert_withTocEntries() throws Exception {
        File inputDir = prepareInputWithToc();
        File output = tempDir.resolve("output_toc.epub").toFile();

        converter.convert(inputDir, "目次テスト", "著者", output);

        assertTrue(output.isFile());
        try (ZipFile zip = new ZipFile(output)) {
            String ncx = readZipEntry(zip, "OEBPS/toc.ncx");
            // TOCに見出しが含まれる
            assertTrue(ncx.contains("第一章"));
            assertTrue(ncx.contains("第二章"));
        }
    }

    @Test
    void testConvert_withCssResource() throws Exception {
        File inputDir = prepareInputWithCss();
        File output = tempDir.resolve("output_css.epub").toFile();

        converter.convert(inputDir, "CSSテスト", "著者", output);

        assertTrue(output.isFile());
        try (ZipFile zip = new ZipFile(output)) {
            assertNotNull(zip.getEntry("OEBPS/aozora.css"));
        }
    }

    @Test
    void testConvert_withImageResource() throws Exception {
        File inputDir = prepareInputWithImage();
        File output = tempDir.resolve("output_img.epub").toFile();

        converter.convert(inputDir, "画像テスト", "著者", output);

        assertTrue(output.isFile());
        try (ZipFile zip = new ZipFile(output)) {
            assertNotNull(zip.getEntry("OEBPS/image.png"));
        }
    }

    @Test
    void testConvert_nullInputDir() {
        File output = tempDir.resolve("output.epub").toFile();
        assertThrows(IOException.class, () -> converter.convert(null, "title", "author", output));
    }

    @Test
    void testConvert_nonExistentInputDir() {
        File fakeDir = tempDir.resolve("nonexistent").toFile();
        File output = tempDir.resolve("output.epub").toFile();
        assertThrows(IOException.class, () -> converter.convert(fakeDir, "title", "author", output));
    }

    @Test
    void testConvert_missingChapterXhtml() throws IOException {
        File inputDir = tempDir.resolve("empty_dir").toFile();
        inputDir.mkdirs();
        File output = tempDir.resolve("output.epub").toFile();

        IOException ex = assertThrows(IOException.class,
                () -> converter.convert(inputDir, "title", "author", output));
        assertTrue(ex.getMessage().contains("chapter1.xhtml"));
    }

    @Test
    void testConvert_emptyAuthor() throws Exception {
        File inputDir = prepareMinimalInput();
        File output = tempDir.resolve("output_noauthor.epub").toFile();

        // null著者でもエラーにならない
        File result = converter.convert(inputDir, "タイトル", null, output);
        assertTrue(result.isFile());
    }

    @Test
    void testConvert_outputInSubdirectory() throws Exception {
        File inputDir = prepareMinimalInput();
        File output = tempDir.resolve("sub/dir/output.epub").toFile();

        File result = converter.convert(inputDir, "サブディレクトリ", "著者", output);
        assertTrue(result.isFile());
        assertTrue(output.getParentFile().isDirectory());
    }

    // ========== ヘルパーメソッド ==========

    private File prepareMinimalInput() throws IOException {
        File dir = tempDir.resolve("input_minimal").toFile();
        dir.mkdirs();

        String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                + "<head><title>テスト</title></head>\n"
                + "<body>\n"
                + "<p>本文テキスト</p>\n"
                + "</body></html>";

        Files.write(new File(dir, "chapter1.xhtml").toPath(),
                xhtml.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private File prepareInputWithToc() throws IOException {
        File dir = tempDir.resolve("input_toc").toFile();
        dir.mkdirs();

        String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                + "<head><title>目次テスト</title></head>\n"
                + "<body>\n"
                + "<h3><a class=\"midashi_anchor\" id=\"midashi1\">第一章</a></h3>\n"
                + "<p>第一章の内容</p>\n"
                + "<h3><a class=\"midashi_anchor\" id=\"midashi2\">第二章</a></h3>\n"
                + "<p>第二章の内容</p>\n"
                + "</body></html>";

        Files.write(new File(dir, "chapter1.xhtml").toPath(),
                xhtml.getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private File prepareInputWithCss() throws IOException {
        File dir = tempDir.resolve("input_css").toFile();
        dir.mkdirs();

        String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                + "<head><title>CSSテスト</title></head>\n"
                + "<body><p>テキスト</p></body></html>";

        Files.write(new File(dir, "chapter1.xhtml").toPath(),
                xhtml.getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir, "aozora.css").toPath(),
                "body { margin: 0; }".getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private File prepareInputWithImage() throws IOException {
        File dir = tempDir.resolve("input_img").toFile();
        dir.mkdirs();

        String xhtml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
                + "<head><title>画像テスト</title></head>\n"
                + "<body><p><img src=\"image.png\" /></p></body></html>";

        Files.write(new File(dir, "chapter1.xhtml").toPath(),
                xhtml.getBytes(StandardCharsets.UTF_8));

        // 1x1 PNG (最小のPNGバイナリ)
        byte[] minPng = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,  // PNG signature
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,          // IHDR chunk
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,          // 1x1
                0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
                (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,  // IDAT chunk
                0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF,
                (byte) 0xC0, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01,
                (byte) 0xE2, 0x21, (byte) 0xBC, 0x33,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,          // IEND chunk
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
        Files.write(new File(dir, "image.png").toPath(), minPng);
        return dir;
    }

    private String readZipEntry(ZipFile zip, String entryName) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) return "";
        byte[] bytes = zip.getInputStream(entry).readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
