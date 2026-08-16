package org.androiddaisyreader.chattyconvert.converter;

import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Date;
import nl.siegmann.epublib.domain.Identifier;
import nl.siegmann.epublib.domain.Metadata;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.epub.EpubWriter;
import nl.siegmann.epublib.service.MediatypeService;
import org.androiddaisyreader.chattyconvert.exception.ChattyConvertException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 解析済みの図書データと音声情報から、epublib（改修版）を用いてEPUB3マルチメディアオーバーレイ形式の
 * ファイルを組み立てて出力する。
 */
public class EpubAssembler {

    private final XhtmlGenerator xhtmlGenerator = new XhtmlGenerator();
    private final SmilGenerator smilGenerator = new SmilGenerator();
    private final NavGenerator navGenerator = new NavGenerator();

    /**
     * EPUBファイルを組み立てて出力する。
     *
     * @param data       図書データ
     * @param audioMap   spanId をキーとする音声クリップのマップ
     * @param inputDir   入力ディレクトリ
     * @param outputFile 出力EPUBファイル
     * @throws IOException         入出力エラー時
     * @throws ChattyConvertException 変換失敗時
     */
    public void assemble(BookData data, Map<String, AudioItem> audioMap,
                         File inputDir, File outputFile) throws IOException, ChattyConvertException {
        Book book = new Book();
        buildMetadata(book, data);
        scanStaticResources(book, data, inputDir);
        addNavResource(book, data);
        addChapters(book, data, audioMap);
        addCover(book, data, inputDir);

        if (book.getSpine().getSpineReferences().isEmpty()) {
            throw new ChattyConvertException("スパインに章が含まれていません");
        }

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("出力ディレクトリを作成できません: " + parent);
        }
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            new EpubWriter().write(book, fos);
        }
    }

    private void buildMetadata(Book book, BookData data) {
        Metadata md = book.getMetadata();
        if (isNotBlank(data.getTitle())) {
            md.addTitle(data.getTitle());
        }
        if (isNotBlank(data.getAuthor())) {
            md.addAuthor(new Author(data.getAuthor(), ""));
        }
        if (isNotBlank(data.getPublisher())) {
            md.addPublisher(data.getPublisher());
        }
        md.setLanguage("ja");
        if (isNotBlank(data.getUuid())) {
            md.addIdentifier(new Identifier(Identifier.Scheme.UUID, data.getUuid()));
        }
        if (isNotBlank(data.getPublicationDate())) {
            md.addDate(new Date(data.getPublicationDate(), Date.Event.PUBLICATION));
        }
    }

    private void addChapters(Book book, BookData data, Map<String, AudioItem> audioMap) throws IOException {
        double totalSeconds = 0.0;
        for (Chapter chapter : data.getChapters()) {
            String xhtmlHref = String.format(Locale.ROOT, "chapter_%04d.xhtml", chapter.getIndex());
            String xhtmlId = String.format(Locale.ROOT, "chapter_%04d", chapter.getIndex());
            String smilHref = String.format(Locale.ROOT, "smil/chapter_%04d.smil", chapter.getIndex());
            String smilId = String.format(Locale.ROOT, "smil_%04d", chapter.getIndex());

            String xhtmlContent = xhtmlGenerator.generate(chapter, data.getCssFiles());
            Resource xhtmlResource = new Resource(xhtmlId,
                    xhtmlContent.getBytes(StandardCharsets.UTF_8), xhtmlHref,
                    MediatypeService.XHTML, StandardCharsets.UTF_8.name());
            book.getResources().add(xhtmlResource);

            String smilContent = smilGenerator.generate(chapter, audioMap, "../" + xhtmlHref);
            Resource smilResource = new Resource(smilId,
                    smilContent.getBytes(StandardCharsets.UTF_8), smilHref,
                    MediatypeService.SMIL, StandardCharsets.UTF_8.name());
            book.getResources().add(smilResource);

            SpineReference spineReference = new SpineReference(xhtmlResource);
            spineReference.setMediaOverlay(smilId);
            book.getSpine().addSpineReference(spineReference);

            book.getTableOfContents().addTOCReference(new TOCReference(chapter.getTitle(), xhtmlResource));

            double chapterSeconds = computeChapterDuration(chapter, audioMap);
            book.getMediaOverlayDurations().put(xhtmlId, formatDuration(chapterSeconds));
            totalSeconds += chapterSeconds;
        }
        book.setMediaTotalDuration(formatDuration(totalSeconds));
    }

    private double computeChapterDuration(Chapter chapter, Map<String, AudioItem> audioMap) {
        String prefix = "s" + String.format(Locale.ROOT, "%03d", chapter.getIndex()) + "_";
        double maxEnd = 0.0;
        for (Map.Entry<String, AudioItem> entry : audioMap.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                maxEnd = Math.max(maxEnd, entry.getValue().getEndSeconds());
            }
        }
        return maxEnd;
    }

    private void addNavResource(Book book, BookData data) {
        String navContent = navGenerator.generate(data, data.getTitle());
        Resource navResource = new Resource("nav",
                navContent.getBytes(StandardCharsets.UTF_8), "nav.xhtml",
                MediatypeService.XHTML, StandardCharsets.UTF_8.name());
        navResource.setProperties("nav");
        book.getResources().add(navResource);
    }

    private void scanStaticResources(Book book, BookData data, File inputDir) throws IOException {
        File cssDir = new File(inputDir, "css");
        if (cssDir.isDirectory()) {
            for (File file : listFiles(cssDir)) {
                String href = "css/" + file.getName();
                data.getCssFiles().add(href);
                book.getResources().add(new Resource(file.getName(), Files.readAllBytes(file.toPath()),
                        href, MediatypeService.CSS, StandardCharsets.UTF_8.name()));
            }
        }

        File imagesDir = new File(inputDir, "images");
        if (imagesDir.isDirectory()) {
            for (File file : listFiles(imagesDir)) {
                if (data.getCoverImage() != null && file.getName().equalsIgnoreCase(new File(data.getCoverImage()).getName())) {
                    continue;
                }
                String href = "images/" + file.getName();
                data.getImageFiles().add(href);
                book.getResources().add(new Resource(file.getName(), Files.readAllBytes(file.toPath()),
                        href, determineImageMediaType(file.getName()), null));
            }
        }

        File soundsDir = new File(inputDir, "sounds");
        if (soundsDir.isDirectory()) {
            for (File file : listFiles(soundsDir)) {
                String href = "sounds/" + file.getName();
                data.getSoundFiles().add(href);
                book.getResources().add(new Resource(file.getName(), Files.readAllBytes(file.toPath()),
                        href, MediatypeService.MP3, null));
            }
        }
    }

    private void addCover(Book book, BookData data, File inputDir) throws IOException {
        if (isBlank(data.getCoverImage())) {
            return;
        }
        File coverFile = new File(inputDir, data.getCoverImage().replace('/', File.separatorChar));
        if (!coverFile.isFile()) {
            return;
        }
        String href = data.getCoverImage();
        Resource cover = new Resource("cover", Files.readAllBytes(coverFile.toPath()),
                href, MediatypeService.determineMediaType(coverFile.getName()), null);
        cover.setProperties("cover-image");
        book.setCoverImage(cover);
    }

    private static List<File> listFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return new ArrayList<>();
        }
        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (file.isFile()) {
                result.add(file);
            }
        }
        return result;
    }

    private static nl.siegmann.epublib.domain.MediaType determineImageMediaType(String filename) {
        nl.siegmann.epublib.domain.MediaType mediaType = MediatypeService.determineMediaType(filename);
        if (mediaType == null) {
            mediaType = MediatypeService.PNG;
        }
        return mediaType;
    }

    /**
     * 秒数をSMIL clock-value形式（H:MM:SS.mmm）に変換する。
     */
    static String formatDuration(double totalSeconds) {
        if (totalSeconds < 0 || Double.isNaN(totalSeconds)) {
            totalSeconds = 0;
        }
        long whole = (long) totalSeconds;
        int hours = (int) (whole / 3600);
        int minutes = (int) ((whole % 3600) / 60);
        int seconds = (int) (whole % 60);
        int millis = (int) Math.round((totalSeconds - whole) * 1000);
        if (millis == 1000) {
            millis = 0;
            seconds++;
            if (seconds == 60) {
                seconds = 0;
                minutes++;
                if (minutes == 60) {
                    minutes = 0;
                    hours++;
                }
            }
        }
        return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
