package org.androiddaisyreader.mykohoconvert.pdf;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PDFから抽出したテキストブロックと単一のMP3から、
 * EPUB3マルチメディアオーバーレイ（テキスト＋音声同期）を生成する。
 *
 * <p>「ページ＝章」「ブロック＝セクション」とし、単一のmp3ファイル上の時刻範囲
 * （clipBegin/clipEnd）を各ブロックのメディアオーバーレイとして割り当てる。</p>
 */
public class PdfEpubConverter {

    private static final Logger logger = LoggerFactory.getLogger(PdfEpubConverter.class);

    private static final String AUDIO_HREF = "audio/audio.mp3";
    private static final int MIN_BLOCK_CHARS = 6;

    /**
     * テキストブロック・ページ画像・MP3からEPUBファイルを生成する。
     *
     * @param blocks     テキストブロック一覧（時刻設定済み）
     * @param pageImages ページ画像（ページ番号→JPEGバイト列）。無い場合は null
     * @param mp3File    MP3ファイル
     * @param title      書籍タイトル
     * @param outputFile 出力EPUBファイル
     * @return 生成されたEPUBファイル
     * @throws IOException 入出力エラー時
     */
    public File convert(List<TextBlock> blocks, Map<Integer, byte[]> pageImages, File mp3File,
                        String title, File outputFile) throws IOException {
        // ページごとにブロックをまとめる（短すぎる断片は除外）
        Map<Integer, List<TextBlock>> byPage = new LinkedHashMap<>();
        for (TextBlock b : blocks) {
            if (b.getCharCount() < MIN_BLOCK_CHARS) {
                continue;
            }
            byPage.computeIfAbsent(b.getPage(), k -> new ArrayList<>()).add(b);
        }

        Book book = new Book();
        Metadata md = book.getMetadata();
        md.addTitle(title != null && !title.isEmpty() ? title : "広報");
        md.setLanguage("ja");

        byte[] audio = Files.readAllBytes(mp3File.toPath());
        book.getResources().add(new Resource("audio", audio, AUDIO_HREF, MediatypeService.MP3, null));

        // ページ画像リソース
        for (Map.Entry<Integer, byte[]> image : (pageImages != null ? pageImages : new LinkedHashMap<Integer, byte[]>()).entrySet()) {
            book.getResources().add(new Resource("img_p" + image.getKey(), image.getValue(),
                    String.format(Locale.ROOT, "images/page_%04d.jpg", image.getKey()),
                    MediatypeService.JPG, null));
        }

        double totalSeconds = 0.0;
        for (Map.Entry<Integer, List<TextBlock>> entry : byPage.entrySet()) {
            int page = entry.getKey();
            List<TextBlock> pageBlocks = entry.getValue();

            String xhtmlHref = String.format(Locale.ROOT, "chapter_%04d.xhtml", page);
            String xhtmlId = String.format(Locale.ROOT, "chapter_%04d", page);
            String smilHref = String.format(Locale.ROOT, "smil/chapter_%04d.smil", page);
            String smilId = String.format(Locale.ROOT, "smil_%04d", page);
            String imageHref = (pageImages != null && pageImages.containsKey(page))
                    ? String.format(Locale.ROOT, "images/page_%04d.jpg", page) : null;

            List<Span> spans = new ArrayList<>();
            int k = 0;
            for (TextBlock b : pageBlocks) {
                k++;
                String spanId = String.format(Locale.ROOT, "s%03d_%04d", page, k);
                spans.add(new Span(spanId, b.getLines(), b.getStartTime(), b.getEndTime()));
            }

            String xhtmlContent = buildChapterXhtml(page, imageHref, spans);
            book.getResources().add(new Resource(xhtmlId,
                    xhtmlContent.getBytes(StandardCharsets.UTF_8), xhtmlHref,
                    MediatypeService.XHTML, StandardCharsets.UTF_8.name()));

            String smilContent = buildChapterSmil(page, "../" + xhtmlHref, spans);
            book.getResources().add(new Resource(smilId,
                    smilContent.getBytes(StandardCharsets.UTF_8), smilHref,
                    MediatypeService.SMIL, StandardCharsets.UTF_8.name()));

            SpineReference spineRef = new SpineReference(book.getResources().getByHref(xhtmlHref));
            spineRef.setMediaOverlay(smilId);
            book.getSpine().addSpineReference(spineRef);
            book.getTableOfContents().addTOCReference(
                    new TOCReference("ページ " + page, book.getResources().getByHref(xhtmlHref)));

            double chapterDur = spans.get(spans.size() - 1).end - spans.get(0).start;
            book.getMediaOverlayDurations().put(xhtmlId, formatDuration(chapterDur));
            totalSeconds += chapterDur;
        }
        book.setMediaTotalDuration(formatDuration(totalSeconds));

        String navContent = buildNav(byPage);
        Resource navRes = new Resource("nav", navContent.getBytes(StandardCharsets.UTF_8),
                "nav.xhtml", MediatypeService.XHTML, StandardCharsets.UTF_8.name());
        navRes.setProperties("nav");
        book.getResources().add(navRes);

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("出力ディレクトリを作成できません: " + parent);
        }
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            new EpubWriter().write(book, fos);
        }
        logger.info("EPUBを作成しました: {} ({}ページ)", outputFile.getAbsolutePath(), byPage.size());
        return outputFile;
    }

    private static String buildChapterXhtml(int page, String imageHref, List<Span> spans) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head><meta charset=\"UTF-8\"/><title>ページ ").append(page).append("</title></head>\n");
        sb.append("<body>\n");
        if (imageHref != null) {
            sb.append("<img src=\"").append(imageHref).append("\" alt=\"ページ ").append(page).append("\"/>\n");
        }
        for (Span span : spans) {
            List<String> escaped = new ArrayList<>();
            for (String line : span.lines) {
                escaped.add(escapeXml(line));
            }
            sb.append("<section>\n");
            sb.append("<p><span id=\"").append(span.id).append("\">")
                    .append(String.join("<br/>", escaped)).append("</span></p>\n");
            sb.append("</section>\n");
        }
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private static String buildChapterSmil(int page, String xhtmlHref, List<Span> spans) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<smil xmlns=\"http://www.w3.org/ns/SMIL\" version=\"3.0\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
                .append("epub:prefix=\"z3998: http://www.daisy.org/z3998/2012/vocab/structure/\">\n");
        sb.append("<body>\n");
        sb.append("<seq id=\"seq_").append(page).append("\" ")
                .append("epub:textref=\"").append(xhtmlHref).append("\" ")
                .append("epub:type=\"bodymatter chapter\">\n");
        int par = 1;
        for (Span span : spans) {
            sb.append("<par id=\"par_").append(par++).append("\">\n");
            sb.append("<text src=\"").append(xhtmlHref).append('#').append(span.id).append("\"/>\n");
            sb.append("<audio src=\"../").append(AUDIO_HREF).append("\" ")
                    .append("clipBegin=\"").append(formatClock(span.start)).append("\" ")
                    .append("clipEnd=\"").append(formatClock(span.end)).append("\"/>\n");
            sb.append("</par>\n");
        }
        sb.append("</seq>\n");
        sb.append("</body>\n</smil>");
        return sb.toString();
    }

    private static String buildNav(Map<Integer, List<TextBlock>> byPage) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head><meta charset=\"UTF-8\"/><title>目次</title></head>\n<body>\n");
        sb.append("<nav epub:type=\"toc\" id=\"toc\">\n<h1>目次</h1>\n<ol>\n");
        for (Integer page : byPage.keySet()) {
            sb.append("<li><a href=\"").append(String.format(Locale.ROOT, "chapter_%04d.xhtml", page))
                    .append("\">ページ ").append(page).append("</a></li>\n");
        }
        sb.append("</ol>\n</nav>\n</body>\n</html>");
        return sb.toString();
    }

    private static String formatClock(double seconds) {
        return String.format(Locale.ROOT, "%.3fs", seconds);
    }

    private static String formatDuration(double seconds) {
        if (seconds < 0 || Double.isNaN(seconds)) {
            seconds = 0;
        }
        long whole = (long) seconds;
        int h = (int) (whole / 3600);
        int m = (int) ((whole % 3600) / 60);
        int s = (int) (whole % 60);
        int ms = (int) Math.round((seconds - whole) * 1000);
        if (ms == 1000) {
            ms = 0;
            s++;
        }
        return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", h, m, s, ms);
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return sanitizeXml(value).replace("&", "&amp;").replace("<", "&lt;")
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

    private static class Span {
        final String id;
        final List<String> lines;
        final double start;
        final double end;

        Span(String id, List<String> lines, double start, double end) {
            this.id = id;
            this.lines = lines;
            this.start = start;
            this.end = end;
        }
    }
}
