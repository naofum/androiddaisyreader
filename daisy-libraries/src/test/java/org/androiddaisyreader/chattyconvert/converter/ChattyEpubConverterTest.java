package org.androiddaisyreader.chattyconvert.converter;

import org.androiddaisyreader.chattyconvert.exception.ChattyConvertException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChattyEpubConverterTest {

    @TempDir
    Path tempDir;

    @Test
    void testConvert_sampleEpub() throws Exception {
        File inputDir = prepareInputDir();

        File output = tempDir.resolve("out.epub").toFile();
        new ChattyEpubConverter().convert(inputDir, output);

        assertTrue(output.isFile());
        try (ZipFile zip = new ZipFile(output)) {
            assertNotNull(zip.getEntry("mimetype"));
            assertNotNull(zip.getEntry("META-INF/container.xml"));
            assertNotNull(zip.getEntry("OEBPS/content.opf"));
            assertNotNull(zip.getEntry("OEBPS/nav.xhtml"));
            assertNotNull(zip.getEntry("OEBPS/toc.ncx"));
            assertNotNull(zip.getEntry("OEBPS/chapter_0001.xhtml"));
            assertNotNull(zip.getEntry("OEBPS/chapter_0011.xhtml"));
            assertNotNull(zip.getEntry("OEBPS/smil/chapter_0001.smil"));
            assertNotNull(zip.getEntry("OEBPS/smil/chapter_0011.smil"));
            assertNotNull(zip.getEntry("OEBPS/css/style.css"));
            assertNotNull(zip.getEntry("OEBPS/images/image00001.jpg"));
            assertNotNull(zip.getEntry("OEBPS/sounds/sound00001.mp3"));
            assertNotNull(zip.getEntry("OEBPS/sounds/sound00011.mp3"));

            String opf = readEntry(zip, "OEBPS/content.opf");
            assertTrue(opf.contains("version=\"3.0\""), "OPF must be EPUB3");
            assertTrue(opf.contains("media-overlay=\""), "spine must reference media overlays");
            assertTrue(opf.contains("media:duration"), "OPF must contain media:duration");
            assertTrue(opf.contains("application/smil+xml"), "manifest must contain SMIL items");
            assertTrue(opf.contains("properties=\"nav\""), "manifest must contain nav properties");
            assertTrue(opf.contains("<dc:title>銀河鉄道の夜</dc:title>"), "metadata title must be present");
            assertTrue(opf.contains("<dc:creator"), "metadata author must be present");

            String smil = readEntry(zip, "OEBPS/smil/chapter_0001.smil");
            assertTrue(smil.contains("<seq id=\"seq_1\" epub:textref=\"../chapter_0001.xhtml\""));
            assertTrue(smil.contains("<audio src=\"../sounds/sound00001.mp3\" clipBegin=\"0.03s\" clipEnd=\"2.82s\"/>"));

            String chapter = readEntry(zip, "OEBPS/chapter_0001.xhtml");
            assertTrue(chapter.contains("<html xmlns=\"http://www.w3.org/1999/xhtml\""));
            assertTrue(chapter.contains("id=\"s001_00001\""));
            assertTrue(chapter.contains("<ruby"), "ruby markup must be preserved");
        }
    }

    @Test
    void testConvert_missingIndexHtml() {
        File inputDir = tempDir.toFile();
        File output = tempDir.resolve("out.epub").toFile();

        ChattyConvertException ex = assertThrows(ChattyConvertException.class,
                () -> new ChattyEpubConverter().convert(inputDir, output));
        assertTrue(ex.getMessage().contains("index.html"));
    }

    @Test
    void testConvert_missingSmilJs() throws IOException {
        File inputDir = prepareInputDir();
        Files.deleteIfExists(inputDir.toPath().resolve("scripts/smil.js"));

        File output = tempDir.resolve("out.epub").toFile();

        ChattyConvertException ex = assertThrows(ChattyConvertException.class,
                () -> new ChattyEpubConverter().convert(inputDir, output));
        assertTrue(ex.getMessage().contains("smil.js"));
    }

    private File prepareInputDir() throws IOException {
        File sample = new File("sample_epub");
        assertTrue(sample.isDirectory(), "sample_epub must exist at project root");

        File inputDir = tempDir.resolve("input").toFile();
        copyDirectory(sample, inputDir);

        File soundsDir = new File(inputDir, "sounds");
        assertTrue(soundsDir.mkdirs());
        byte[] dummy = new byte[]{0x49, 0x44, 0x33};
        for (int i = 1; i <= 11; i++) {
            Files.write(new File(soundsDir, String.format("sound%05d.mp3", i)).toPath(), dummy);
        }
        return inputDir;
    }

    private static void copyDirectory(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) {
                throw new IOException("ディレクトリを作成できません: " + dst);
            }
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyDirectory(child, new File(dst, child.getName()));
                }
            }
        } else {
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        assertNotNull(entry, "zip entry must exist: " + name);
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
