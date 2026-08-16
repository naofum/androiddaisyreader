package org.androiddaisyreader.kohoconvert;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Metadata;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.epub.EpubWriter;
import nl.siegmann.epublib.service.MediatypeService;
import org.androiddaisyreader.kohoconvert.exception.KohoConvertException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 音声版広報からダウンロードされた一時ディレクトリ（index.html, documents/*.mp3）を
 * EPUB3マルチメディアオーバーレイ形式に変換する。
 *
 * <p>音声版広報は音声のみのため、index.html内で音声ファイル（*.mp3）へのリンクを持つ
 * 明細行の見出しを章題・目次として使用し、各章の本文は見出し行の内容のみとする。
 * 各章には対応する音声ファイル（mp3）がメディアオーバーレイとして割り当てられる。</p>
 *
 * <p>使い方:
 * <pre>
 *   KohoEpubConverter converter = new KohoEpubConverter();
 *   File epub = converter.convert(inputDir, outputFile);
 * </pre>
 */
public class KohoEpubConverter {

    private static final Logger logger = LoggerFactory.getLogger(KohoEpubConverter.class);

    private static final Pattern MINUTES_PATTERN = Pattern.compile("(\\d+)分");
    private static final Pattern SECONDS_PATTERN = Pattern.compile("(\\d+)秒");
    private static final Pattern MP3_SIZE_PATTERN = Pattern.compile("[（(]MP3[：:][^（）()]*[）)]");

    /**
     * ダウンロード済みの一時ディレクトリからEPUBを生成する。
     *
     * @param inputDir   入力ディレクトリ（index.html, documents/*.mp3 を含む）
     * @param outputFile 出力EPUBファイル
     * @return 生成されたEPUBファイル
     * @throws KohoConvertException 変換失敗時
     * @throws IOException          入出力エラー時
     */
    public File convert(File inputDir, File outputFile) throws KohoConvertException, IOException {
        if (inputDir == null || !inputDir.isDirectory()) {
            throw new KohoConvertException("入力ディレクトリが存在しません: " + inputDir);
        }

        File indexHtml = new File(inputDir, "index.html");
        if (!indexHtml.isFile()) {
            throw new KohoConvertException("index.htmlが見つかりません: " + indexHtml.getAbsolutePath());
        }

        String html = readFile(indexHtml);
        Document doc = Jsoup.parse(html);
        String title = extractBookTitle(doc);
        List<AudioRow> rows = parseAudioRows(html);

        if (rows.isEmpty()) {
            throw new KohoConvertException("index.htmlから音声ファイルのある明細行が見つかりませんでした");
        }
        logger.info("音声明細行: {}件, タイトル: {}", rows.size(), title);

        Book book = buildBook(title, inputDir, rows);

        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("出力ディレクトリを作成できません: " + parentDir);
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            new EpubWriter().write(book, fos);
        }

        logger.info("EPUBを作成しました: {} ({}章)", outputFile.getAbsolutePath(), rows.size());
        return outputFile;
    }

    /**
     * index.htmlから、音声ファイルへのリンクを持つ明細行を抽出する。
     *
     * @param html index.htmlの内容
     * @return 音声明細行の一覧
     */
    List<AudioRow> parseAudioRows(String html) {
        Document doc = Jsoup.parse(html);
        List<AudioRow> rows = new ArrayList<>();

        for (Element tr : doc.select("tr")) {
            Element link = tr.selectFirst("a[href]");
            if (link == null) {
                continue;
            }
            String href = link.attr("href");
            if (!isAudioHref(href)) {
                continue;
            }
            String title = cleanTitle(link.text());
            String audioFile = "documents/" + extractFileName(href);
            String durationText = extractDurationText(tr);
            double seconds = parseDurationSeconds(durationText);
            rows.add(new AudioRow(title, audioFile, seconds));
        }
        return rows;
    }

    /**
     * EPUBのBookオブジェクトを構築する。
     */
    private Book buildBook(String title, File inputDir, List<AudioRow> rows) throws IOException {
        Book book = new Book();
        Metadata metadata = book.getMetadata();
        metadata.addTitle(title);
        metadata.setLanguage("ja");

        double totalSeconds = 0.0;
        for (int i = 0; i < rows.size(); i++) {
            AudioRow row = rows.get(i);
            int index = i + 1;
            String chapterTitle = row.title.isEmpty() ? "第" + index + "章" : row.title;

            String xhtmlHref = String.format(Locale.ROOT, "chapter_%04d.xhtml", index);
            String xhtmlId = String.format(Locale.ROOT, "chapter_%04d", index);
            String smilHref = String.format(Locale.ROOT, "smil/chapter_%04d.smil", index);
            String smilId = String.format(Locale.ROOT, "smil_%04d", index);
            String spanId = String.format(Locale.ROOT, "s%03d_0001", index);

            // 章XHTML
            String xhtmlContent = buildChapterXhtml(chapterTitle, spanId);
            Resource xhtmlResource = new Resource(xhtmlId,
                    xhtmlContent.getBytes(StandardCharsets.UTF_8), xhtmlHref,
                    MediatypeService.XHTML, StandardCharsets.UTF_8.name());
            book.getResources().add(xhtmlResource);

            // 音声リソース（ファイルが存在する場合のみ）
            boolean hasAudio = false;
            File audioFile = new File(inputDir, row.audioFile);
            if (audioFile.isFile()) {
                book.getResources().add(new Resource(audioFile.getName(),
                        Files.readAllBytes(audioFile.toPath()), row.audioFile,
                        MediatypeService.MP3, null));
                hasAudio = true;
            } else {
                logger.warn("音声ファイルが見つからないため章から除外します: {}", row.audioFile);
            }

            // SMIL（メディアオーバーレイ）
            String smilContent = buildChapterSmil(index, "../" + xhtmlHref, spanId,
                    "../" + row.audioFile, row.durationSeconds, hasAudio);
            Resource smilResource = new Resource(smilId,
                    smilContent.getBytes(StandardCharsets.UTF_8), smilHref,
                    MediatypeService.SMIL, StandardCharsets.UTF_8.name());
            book.getResources().add(smilResource);

            SpineReference spineReference = new SpineReference(xhtmlResource);
            spineReference.setMediaOverlay(smilId);
            book.getSpine().addSpineReference(spineReference);

            book.getTableOfContents().addTOCReference(new TOCReference(chapterTitle, xhtmlResource));

            book.getMediaOverlayDurations().put(xhtmlId, formatDuration(row.durationSeconds));
            totalSeconds += row.durationSeconds;
        }
        book.setMediaTotalDuration(formatDuration(totalSeconds));

        // ナビゲーション（目次）
        String navContent = buildNav(rows);
        Resource navResource = new Resource("nav",
                navContent.getBytes(StandardCharsets.UTF_8), "nav.xhtml",
                MediatypeService.XHTML, StandardCharsets.UTF_8.name());
        navResource.setProperties("nav");
        book.getResources().add(navResource);

        return book;
    }

    /**
     * 章のXHTMLを生成する。本文は見出し行のみ（span要素1つ）。
     */
    private String buildChapterXhtml(String title, String spanId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head><meta charset=\"UTF-8\"/><title>").append(escapeXml(title)).append("</title></head>\n");
        sb.append("<body>\n");
        sb.append("<h2>").append(escapeXml(title)).append("</h2>\n");
        sb.append("<p><span id=\"").append(spanId).append("\">").append(escapeXml(title)).append("</span></p>\n");
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    /**
     * 章のSMIL（メディアオーバーレイ）を生成する。
     */
    private String buildChapterSmil(int index, String xhtmlHref, String spanId,
                                    String audioHref, double durationSeconds, boolean hasAudio) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<smil xmlns=\"http://www.w3.org/ns/SMIL\" version=\"3.0\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
                .append("epub:prefix=\"z3998: http://www.daisy.org/z3998/2012/vocab/structure/\">\n");
        sb.append("<body>\n");
        sb.append("<seq id=\"seq_").append(index).append("\" ")
                .append("epub:textref=\"").append(xhtmlHref).append("\" ")
                .append("epub:type=\"bodymatter chapter\">\n");
        sb.append("<par id=\"par_1\">\n");
        sb.append("<text src=\"").append(xhtmlHref).append('#').append(spanId).append("\"/>\n");
        if (hasAudio) {
            sb.append("<audio src=\"").append(audioHref).append("\"");
            if (durationSeconds > 0) {
                sb.append(" clipBegin=\"0.000s\" clipEnd=\"").append(formatClock(durationSeconds)).append("\"");
            }
            sb.append("/>\n");
        }
        sb.append("</par>\n");
        sb.append("</seq>\n");
        sb.append("</body>\n</smil>");
        return sb.toString();
    }

    /**
     * 目次（nav.xhtml）を生成する。
     */
    private String buildNav(List<AudioRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head><meta charset=\"UTF-8\"/><title>目次</title></head>\n<body>\n");
        sb.append("<nav epub:type=\"toc\" id=\"toc\">\n<h1>目次</h1>\n<ol>\n");
        for (int i = 0; i < rows.size(); i++) {
            String href = String.format(Locale.ROOT, "chapter_%04d.xhtml", i + 1);
            String title = rows.get(i).title;
            sb.append("<li><a href=\"").append(href).append("\">")
                    .append(escapeXml(title)).append("</a></li>\n");
        }
        sb.append("</ol>\n</nav>\n</body>\n</html>");
        return sb.toString();
    }

    /**
     * index.htmlから書籍タイトルを抽出する。
     */
    String extractBookTitle(Document doc) {
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().trim().isEmpty()) {
            return h1.text().trim();
        }
        String title = doc.title();
        if (title != null && !title.trim().isEmpty()) {
            return title.trim();
        }
        return "音声版広報";
    }

    private static boolean isAudioHref(String href) {
        String path = href;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a")
                || lower.endsWith(".wav") || lower.endsWith(".ogg");
    }

    /**
     * リンクテキストから「（MP3：xxKB）」等の付加情報を除去して見出しを返す。
     */
    String cleanTitle(String linkText) {
        if (linkText == null) {
            return "";
        }
        String cleaned = MP3_SIZE_PATTERN.matcher(linkText).replaceAll("");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private static String extractDurationText(Element tr) {
        Elements tds = tr.select("td");
        if (tds.isEmpty()) {
            return "";
        }
        return tds.last().text();
    }

    /**
     * 「2分04秒」のような所要時間表記を秒数に変換する。
     */
    double parseDurationSeconds(String text) {
        if (text == null) {
            return 0.0;
        }
        int minutes = 0;
        int seconds = 0;
        Matcher minuteMatcher = MINUTES_PATTERN.matcher(text);
        if (minuteMatcher.find()) {
            minutes = Integer.parseInt(minuteMatcher.group(1));
        }
        Matcher secondMatcher = SECONDS_PATTERN.matcher(text);
        if (secondMatcher.find()) {
            seconds = Integer.parseInt(secondMatcher.group(1));
        }
        return minutes * 60 + seconds;
    }

    private static String extractFileName(String href) {
        String path = href;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
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

    /**
     * 秒数をSMILのclip値形式（例: 5.000s）に変換する。
     */
    private static String formatClock(double seconds) {
        return String.format(Locale.ROOT, "%.3fs", seconds);
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * 音声ファイルのある明細行の内部表現。
     */
    static class AudioRow {
        final String title;
        final String audioFile;
        final double durationSeconds;

        AudioRow(String title, String audioFile, double durationSeconds) {
            this.title = title;
            this.audioFile = audioFile;
            this.durationSeconds = durationSeconds;
        }
    }
}
