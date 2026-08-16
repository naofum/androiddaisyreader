package org.androiddaisyreader.voicepageconvert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceEpubConverterTest {

    @TempDir
    Path tempDir;

    @Test
    void convert_createsChapterPerMp3() throws Exception {
        List<VoiceEpubConverter.AudioItem> items = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            File mp3 = tempDir.resolve("audio_000" + i + ".mp3").toFile();
            Files.write(mp3.toPath(), new byte[]{0x49, 0x44, 0x33});
            items.add(new VoiceEpubConverter.AudioItem("2026年8月5日号　" + i + "面", mp3));
        }

        File output = tempDir.resolve("voice.epub").toFile();
        new VoiceEpubConverter().convert("市報こだいら", items, output);

        assertTrue(output.isFile());
        try (ZipFile zip = new ZipFile(output)) {
            assertNotNull(zip.getEntry("OEBPS/content.opf"));
            assertNotNull(zip.getEntry("OEBPS/nav.xhtml"));
            assertNotNull(zip.getEntry("OEBPS/chapter_0001.xhtml"));
            assertNotNull(zip.getEntry("OEBPS/chapter_0003.xhtml"));
            assertNotNull(zip.getEntry("OEBPS/smil/chapter_0001.smil"));
            assertNotNull(zip.getEntry("OEBPS/smil/chapter_0003.smil"));
            assertNotNull(zip.getEntry("OEBPS/audio/audio_0001.mp3"));

            String opf = readEntry(zip, "OEBPS/content.opf");
            assertTrue(opf.contains("version=\"3.0\""), "OPF must be EPUB3");
            assertTrue(opf.contains("media-overlay=\"smil_0001\""), "spine must reference media overlay");
            assertTrue(opf.contains("application/smil+xml"), "manifest must contain SMIL items");

            String smil = readEntry(zip, "OEBPS/smil/chapter_0001.smil");
            assertTrue(smil.contains("<text src=\"../chapter_0001.xhtml#s001_0001\"/>"));
            assertTrue(smil.contains("<audio src=\"../audio/audio_0001.mp3\"/>"));

            String chapter = readEntry(zip, "OEBPS/chapter_0001.xhtml");
            assertTrue(chapter.contains("<span id=\"s001_0001\">"));
            assertTrue(chapter.contains("2026年8月5日号　1面"));
        }
    }

    @Test
    void convert_emptyItemsThrows() throws Exception {
        File output = tempDir.resolve("empty.epub").toFile();
        java.io.IOException ex = org.junit.jupiter.api.Assertions.assertThrows(
                java.io.IOException.class,
                () -> new VoiceEpubConverter().convert("title", new ArrayList<>(), output));
        assertTrue(ex.getMessage().contains("音声項目"));
    }

    private static String readEntry(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        assertNotNull(entry, "zip entry must exist: " + name);
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
