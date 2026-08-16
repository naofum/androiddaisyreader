package org.androiddaisyreader.voicepageconvert;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Metadata;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.epub.EpubWriter;
import nl.siegmann.epublib.service.MediatypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * 音声エントリ（タイトル＋mp3）から EPUB3 メディアオーバーレイを生成する。
 * mp3 1件ごとに chapter_0001.xhtml / smil/chapter_0001.smil を連番で作成し、
 * 各章が1つのセクションになるようにする。
 */
public class VoiceEpubConverter {

    private static final Logger logger = LoggerFactory.getLogger(VoiceEpubConverter.class);

    /** 1つの音声項目（タイトル＋ローカルmp3ファイル）。 */
    public static class AudioItem {
        public final String title;
        public final File file;

        public AudioItem(String title, File file) {
            this.title = title;
            this.file = file;
        }
    }

    /**
     * EPUB3 メディアオーバーレイを生成して出力する。
     *
     * @param title      書籍タイトル
     * @param items      音声項目一覧
     * @param outputFile 出力EPUBファイル
     * @return 生成されたEPUBファイル
     * @throws IOException 入出力エラー時
     */
    public File convert(String title, List<AudioItem> items, File outputFile) throws IOException {
        if (items.isEmpty()) {
            throw new IOException("音声項目がありません");
        }
        String bookTitle = title != null && !title.isEmpty() ? title : "音声版広報";

        Book book = new Book();
        Metadata md = book.getMetadata();
        md.addTitle(bookTitle);
        md.setLanguage("ja");

        int index = 1;
        for (AudioItem item : items) {
            String chapterId = String.format(Locale.ROOT, "chapter_%04d", index);
            String xhtmlHref = String.format(Locale.ROOT, "chapter_%04d.xhtml", index);
            String smilId = String.format(Locale.ROOT, "smil_%04d", index);
            String smilHref = String.format(Locale.ROOT, "smil/chapter_%04d.smil", index);
            String spanId = String.format(Locale.ROOT, "s%03d_0001", index);
            String audioHref = String.format(Locale.ROOT, "audio/audio_%04d.mp3", index);

            // 音声リソース
            book.getResources().add(new Resource("audio_" + index,
                    Files.readAllBytes(item.file.toPath()), audioHref,
                    MediatypeService.MP3, null));

            // 章XHTML
            String xhtmlContent = buildChapterXhtml(item.title, spanId);
            Resource xhtmlResource = new Resource(chapterId,
                    xhtmlContent.getBytes(StandardCharsets.UTF_8), xhtmlHref,
                    MediatypeService.XHTML, StandardCharsets.UTF_8.name());
            book.getResources().add(xhtmlResource);

            // 章SMIL（smil/ 配下に置くため、参照は ../ を付与）
            String smilContent = buildChapterSmil("../" + xhtmlHref, spanId, "../" + audioHref);
            Resource smilResource = new Resource(smilId,
                    smilContent.getBytes(StandardCharsets.UTF_8), smilHref,
                    MediatypeService.SMIL, StandardCharsets.UTF_8.name());
            book.getResources().add(smilResource);

            // スパイン（メディアオーバーレイ付き）
            SpineReference spineRef = new SpineReference(xhtmlResource);
            spineRef.setMediaOverlay(smilId);
            book.getSpine().addSpineReference(spineRef);

            // 目次
            book.getTableOfContents().addTOCReference(new TOCReference(item.title, xhtmlResource));

            index++;
        }

        // ナビゲーション（目次）
        String navContent = buildNav(items);
        Resource navRes = new Resource("nav",
                navContent.getBytes(StandardCharsets.UTF_8), "nav.xhtml",
                MediatypeService.XHTML, StandardCharsets.UTF_8.name());
        navRes.setProperties("nav");
        book.getResources().add(navRes);

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("出力ディレクトリを作成できません: " + parent);
        }
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            new EpubWriter().write(book, fos);
        }
        logger.info("EPUBを出力しました: {} ({}章)", outputFile.getAbsolutePath(), items.size());
        return outputFile;
    }

    private String buildChapterXhtml(String title, String spanId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head><meta charset=\"UTF-8\"/><title>")
                .append(escapeXml(title)).append("</title></head>\n");
        sb.append("<body>\n");
        sb.append("<h2>").append(escapeXml(title)).append("</h2>\n");
        sb.append("<p><span id=\"").append(spanId).append("\">")
                .append(escapeXml(title)).append("</span></p>\n");
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private String buildChapterSmil(String xhtmlHref, String spanId, String audioHref) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<smil xmlns=\"http://www.w3.org/ns/SMIL\" version=\"3.0\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
                .append("epub:prefix=\"z3998: http://www.daisy.org/z3998/2012/vocab/structure/\">\n");
        sb.append("<body>\n");
        sb.append("<seq id=\"seq_1\" epub:textref=\"").append(xhtmlHref).append("\" ")
                .append("epub:type=\"bodymatter chapter\">\n");
        sb.append("<par id=\"par_1\">\n");
        sb.append("<text src=\"").append(xhtmlHref).append('#').append(spanId).append("\"/>\n");
        sb.append("<audio src=\"").append(audioHref).append("\"/>\n");
        sb.append("</par>\n");
        sb.append("</seq>\n");
        sb.append("</body>\n</smil>");
        return sb.toString();
    }

    private String buildNav(List<AudioItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head><meta charset=\"UTF-8\"/><title>目次</title></head>\n<body>\n");
        sb.append("<nav epub:type=\"toc\" id=\"toc\">\n<ol>\n");
        int index = 1;
        for (AudioItem item : items) {
            String href = String.format(Locale.ROOT, "chapter_%04d.xhtml", index++);
            sb.append("<li><a href=\"").append(href).append("\">")
                    .append(escapeXml(item.title)).append("</a></li>\n");
        }
        sb.append("</ol>\n</nav>\n</body>\n</html>");
        return sb.toString();
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return sanitizeXml(value)
                .replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * XML 1.0 で許容されない制御文字を除去する。
     */
    private static String sanitizeXml(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0x9 || cp == 0xA || cp == 0xD
                    || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD)
                    || (cp >= 0x10000 && cp <= 0x10FFFF)) {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }
}
