package org.androiddaisyreader.aozoraconvert;

import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Metadata;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.SpineReference;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.epub.EpubWriter;
import nl.siegmann.epublib.service.MediatypeService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
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

/**
 * 青空文庫からダウンロードされた一時ディレクトリの内容をEPUB3形式に変換する。
 * 見出し（.midashi_anchor）ごとに章分割してEPUBを生成する。
 *
 * <p>使い方:
 * <pre>
 *   AozoraEpubConverter converter = new AozoraEpubConverter();
 *   File epub = converter.convert(inputDir, "作品名", "著者名", outputFile);
 * </pre>
 */
public class AozoraEpubConverter {

    private static final Logger logger = LoggerFactory.getLogger(AozoraEpubConverter.class);

    /** 見出しがない場合のフォールバック分割サイズ（文字数） */
    private static final int MAX_CHAPTER_CHARS = 10000;

    /**
     * ダウンロード済みの一時ディレクトリからEPUBを生成する。
     *
     * @param inputDir   入力ディレクトリ（chapter1.xhtml, CSS, 画像を含む）
     * @param title      作品タイトル
     * @param author     著者名
     * @param outputFile 出力EPUBファイル
     * @return 生成されたEPUBファイル
     * @throws IOException 入出力エラー時
     */
    public File convert(File inputDir, String title, String author, File outputFile) throws IOException {
        if (inputDir == null || !inputDir.isDirectory()) {
            throw new IOException("入力ディレクトリが存在しません: " + inputDir);
        }

        File xhtmlFile = new File(inputDir, "chapter1.xhtml");
        if (!xhtmlFile.isFile()) {
            throw new IOException("chapter1.xhtmlが見つかりません: " + xhtmlFile.getAbsolutePath());
        }

        logger.info("Converting to EPUB: title={}, author={}", title, author);

        // XHTMLコンテンツ読み込み
        byte[] xhtmlBytes = Files.readAllBytes(xhtmlFile.toPath());
        String xhtmlContent = new String(xhtmlBytes, StandardCharsets.UTF_8);

        // DOMパース
        Document document = Jsoup.parse(xhtmlContent);
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

        // head要素を取得（CSS link等）
        Element head = document.head();
        String headHtml = head != null ? head.html() : "";

        // CSSファイル名を収集
        List<String> cssFiles = new ArrayList<>();
        File[] cssFileList = inputDir.listFiles((dir, name) -> name.endsWith(".css"));
        if (cssFileList != null) {
            for (File css : cssFileList) {
                cssFiles.add(css.getName());
            }
        }

        // 章に分割
        List<ChapterData> chapters = splitIntoChapters(document, title);
        logger.info("Split into {} chapters", chapters.size());

        // Bookオブジェクト構築
        Book book = new Book();
        buildMetadata(book, title, author);

        // CSSリソース追加
        addCssResources(book, inputDir);

        // 画像リソース追加
        addImageResources(book, inputDir);

        // 各章をXHTMLリソースとして追加
        for (int i = 0; i < chapters.size(); i++) {
            ChapterData chapter = chapters.get(i);
            String chapterHref = String.format(Locale.ROOT, "chapter_%04d.xhtml", i + 1);
            String chapterId = String.format(Locale.ROOT, "chapter_%04d", i + 1);

            String chapterXhtml = buildChapterXhtml(chapter, headHtml, cssFiles, i + 1);
            byte[] chapterBytes = chapterXhtml.getBytes(StandardCharsets.UTF_8);

            Resource resource = new Resource(
                    chapterId, chapterBytes, chapterHref,
                    MediatypeService.XHTML, StandardCharsets.UTF_8.name());
            book.addSection(chapter.title, resource);

            // TOC
            book.getTableOfContents().addTOCReference(
                    new TOCReference(chapter.title, resource));
        }

        // EPUB書き出し
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("出力ディレクトリを作成できません: " + parentDir);
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            EpubWriter epubWriter = new EpubWriter();
            epubWriter.write(book, fos);
        }

        logger.info("EPUB created: {} ({} chapters)", outputFile.getAbsolutePath(), chapters.size());
        return outputFile;
    }

    /**
     * ドキュメントを見出しごとに章分割する。
     * 見出しがない場合は一定文字数で分割する。
     */
    private List<ChapterData> splitIntoChapters(Document document, String bookTitle) {
        List<ChapterData> chapters = new ArrayList<>();

        // body 内の見出し要素を探す（.midashi_anchor の親、または h1-h6）
        Element body = document.body();
        if (body == null) {
            // bodyがない場合はフォールバック
            chapters.add(new ChapterData(bookTitle, document.html()));
            return chapters;
        }

        // 見出し位置を特定：.midashi_anchor を含む要素、またはトップレベルの h1-h6
        List<Element> headingElements = new ArrayList<>();
        Elements midashiAnchors = body.select(".midashi_anchor");
        for (Element anchor : midashiAnchors) {
            // 見出しの親（h3, h4 等）を取得
            Element parent = anchor.parent();
            if (parent != null && parent.tagName().matches("h[1-6]")) {
                headingElements.add(parent);
            } else {
                headingElements.add(anchor);
            }
        }

        // 見出しがない場合：トップレベル h1-h6 を探す
        if (headingElements.isEmpty()) {
            Elements headings = body.select("h1, h2, h3, h4, h5, h6");
            for (Element h : headings) {
                headingElements.add(h);
            }
        }

        // 見出しがまだない場合：サイズベースで分割
        if (headingElements.isEmpty()) {
            chapters.addAll(splitBySize(body, bookTitle));
            return chapters;
        }

        // 見出しで分割
        // body の直下子要素を走査し、見出しが現れるたびに新しい章を開始
        List<Node> bodyChildren = body.childNodes();
        StringBuilder currentContent = new StringBuilder();
        String currentTitle = bookTitle;  // 最初の見出し前のコンテンツ用
        boolean hasContentBeforeFirstHeading = false;

        for (int i = 0; i < bodyChildren.size(); i++) {
            Node node = bodyChildren.get(i);
            Element element = (node instanceof Element) ? (Element) node : null;

            boolean isHeading = false;
            String headingText = null;

            if (element != null) {
                // この要素自体が見出しか、見出しを含むか
                if (headingElements.contains(element)) {
                    isHeading = true;
                    headingText = element.text();
                } else if (element.tagName().matches("h[1-6]")) {
                    isHeading = true;
                    headingText = element.text();
                } else {
                    // 直下の子に見出しがあるか
                    Element foundHeading = element.selectFirst(".midashi_anchor");
                    if (foundHeading == null) {
                        foundHeading = element.selectFirst("h1, h2, h3, h4, h5, h6");
                    }
                    if (foundHeading != null && headingElements.contains(foundHeading)) {
                        isHeading = true;
                        headingText = foundHeading.text();
                    }
                }
            }

            if (isHeading && currentContent.length() > 0) {
                // 前の章を確定
                chapters.add(new ChapterData(currentTitle, currentContent.toString()));
                currentContent.setLength(0);
            }

            if (isHeading && headingText != null && !headingText.isEmpty()) {
                currentTitle = headingText;
            }

            // ノードのHTMLを追加
            if (element != null) {
                currentContent.append(element.outerHtml()).append("\n");
            } else {
                currentContent.append(node.outerHtml());
            }
        }

        // 最後の章
        if (currentContent.length() > 0) {
            chapters.add(new ChapterData(currentTitle, currentContent.toString()));
        }

        // 章が1つしかなく巨大な場合、サイズで再分割
        if (chapters.size() == 1 && chapters.get(0).bodyContent.length() > MAX_CHAPTER_CHARS * 3) {
            Document singleDoc = Jsoup.parse("<body>" + chapters.get(0).bodyContent + "</body>");
            List<ChapterData> reSplit = splitBySize(singleDoc.body(), bookTitle);
            if (reSplit.size() > 1) {
                chapters = reSplit;
            }
        }

        return chapters;
    }

    /**
     * サイズベースで分割する（見出しがない場合のフォールバック）。
     * div 要素の境界で分割する。
     */
    private List<ChapterData> splitBySize(Element body, String bookTitle) {
        List<ChapterData> chapters = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int chapterNum = 1;

        for (Element child : body.children()) {
            current.append(child.outerHtml()).append("\n");

            if (current.length() >= MAX_CHAPTER_CHARS) {
                String chapterTitle = bookTitle + " (" + chapterNum + ")";
                chapters.add(new ChapterData(chapterTitle, current.toString()));
                current.setLength(0);
                chapterNum++;
            }
        }

        // 残り
        if (current.length() > 0) {
            String chapterTitle = chapters.isEmpty() ? bookTitle : bookTitle + " (" + chapterNum + ")";
            chapters.add(new ChapterData(chapterTitle, current.toString()));
        }

        return chapters;
    }

    /**
     * 章のXHTMLドキュメントを組み立てる。
     * 最初のh1-h6要素にchapter IDを付与し、テキストを持つdiv等に連番IDを付与する。
     */
    private String buildChapterXhtml(ChapterData chapter, String headHtml, List<String> cssFiles, int chapterIndex) {
        // bodyContent内の要素にIDを付与
        String bodyContent = assignChapterElementIds(chapter.bodyContent, chapterIndex, chapter.title);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head>\n");
        sb.append("<meta charset=\"UTF-8\"/>\n");
        sb.append("<title>").append(escapeXml(chapter.title)).append("</title>\n");
        for (String css : cssFiles) {
            sb.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"").append(css).append("\"/>\n");
        }
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append(bodyContent);
        sb.append("\n</body>\n</html>");
        return sb.toString();
    }

    /**
     * bodyContent 内の要素にIDを付与する。
     * 最初の h1-h6 に chapter_NNNN を付与し、テキストを持つ div 等に連番IDを付与する。
     * h1-h6 がない場合は先頭に h1 を挿入する。
     */
    private String assignChapterElementIds(String bodyContent, int chapterIndex, String title) {
        Document doc = Jsoup.parse("<body>" + bodyContent + "</body>");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        Element body = doc.body();
        if (body == null) return bodyContent;

        // 最初の h1-h6 に chapter ID を付与
        Element firstHeading = body.selectFirst("h1, h2, h3, h4, h5, h6");
        if (firstHeading != null) {
            firstHeading.attr("id", String.format(Locale.ROOT, "chapter_%04d", chapterIndex));
        } else {
            // h1-h6 がない場合は先頭に非表示の h1 を挿入
            body.prependChild(new Element("h1")
                    .attr("id", String.format(Locale.ROOT, "chapter_%04d", chapterIndex))
                    .attr("style", "display:none")
                    .text(title));
        }

        // テキストを持つ div/p に連番IDを付与
        int idNum = 1;
        for (Element el : body.select("div, p")) {
            if (el.ownText().trim().isEmpty() && !(el.children().isEmpty() && !el.text().trim().isEmpty())) {
                continue;
            }
            el.attr("id", String.format(Locale.ROOT, "s%04d_%04d", chapterIndex, idNum++));
        }

        return body.html();
    }

    /**
     * メタデータを設定する。
     */
    private void buildMetadata(Book book, String title, String author) {
        Metadata metadata = book.getMetadata();
        metadata.addTitle(title);
        metadata.addAuthor(new Author(author != null ? author.trim() : ""));
        metadata.addPublisher("青空文庫");
        metadata.setLanguage("ja");
    }

    /**
     * CSSファイルをリソースとして追加する。
     */
    private void addCssResources(Book book, File inputDir) throws IOException {
        File[] files = inputDir.listFiles((dir, name) -> name.endsWith(".css"));
        if (files == null) return;

        for (File cssFile : files) {
            byte[] content = Files.readAllBytes(cssFile.toPath());
            Resource resource = new Resource(
                    cssFile.getName(),
                    content,
                    cssFile.getName(),
                    MediatypeService.CSS,
                    StandardCharsets.UTF_8.name()
            );
            book.getResources().add(resource);
        }
    }

    /**
     * 画像ファイルをリソースとして追加する。
     */
    private void addImageResources(Book book, File inputDir) throws IOException {
        File[] files = inputDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".png") || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                    || lower.endsWith(".svg");
        });
        if (files == null) return;

        for (File imgFile : files) {
            byte[] content = Files.readAllBytes(imgFile.toPath());
            nl.siegmann.epublib.domain.MediaType mediaType = MediatypeService.determineMediaType(imgFile.getName());
            if (mediaType == null) {
                mediaType = MediatypeService.PNG;
            }
            Resource resource = new Resource(
                    imgFile.getName(),
                    content,
                    imgFile.getName(),
                    mediaType,
                    null
            );
            book.getResources().add(resource);
        }
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 章データの内部表現。
     */
    private static class ChapterData {
        final String title;
        final String bodyContent;

        ChapterData(String title, String bodyContent) {
            this.title = title;
            this.bodyContent = bodyContent;
        }
    }
}
