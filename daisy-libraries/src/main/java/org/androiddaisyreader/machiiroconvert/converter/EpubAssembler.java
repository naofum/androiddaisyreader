package org.androiddaisyreader.machiiroconvert.converter;

import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Identifier;
import nl.siegmann.epublib.domain.MediaType;
import nl.siegmann.epublib.domain.Metadata;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.TOCReference;
import nl.siegmann.epublib.epub.EpubWriter;
import nl.siegmann.epublib.service.MediatypeService;
import org.androiddaisyreader.machiiroconvert.exception.MachiiroConvertException;
import org.androiddaisyreader.machiiroconvert.model.Magazine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 解析済みの広報誌データとページ画像から、epublib（改修版）を用いてEPUBファイルを組み立てて出力する。
 */
public class EpubAssembler {

    private final IndexHtmlGenerator indexHtmlGenerator = new IndexHtmlGenerator();
    private final ChapterGenerator chapterGenerator = new ChapterGenerator();
    private final NavGenerator navGenerator = new NavGenerator();

    /**
     * EPUBファイルを組み立てて出力する。
     *
     * @param magazine   広報誌データ
     * @param pages      ページ一覧（画像・テキストを含む）
     * @param outputFile 出力EPUBファイル
     * @throws IOException               入出力エラー時
     * @throws MachiiroConvertException 変換失敗時
     */
    public void assemble(Magazine magazine, List<Page> pages, File outputFile)
            throws IOException, MachiiroConvertException {
        Book book = new Book();
        buildMetadata(book, magazine);
        Map<Integer, Resource> imageResources = addImageResources(book, pages);
        addNavResource(book, magazine, pages);
        addIndexResource(book, magazine, pages);
        addChapters(book, pages);
        addCover(book, imageResources);

        if (book.getSpine().isEmpty()) {
            throw new MachiiroConvertException("スパインにページが含まれていません");
        }

        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("出力ディレクトリを作成できません: " + parent);
        }
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            new EpubWriter().write(book, fos);
        }
    }

    private void buildMetadata(Book book, Magazine magazine) {
        Metadata md = book.getMetadata();
        String title = bookTitle(magazine);
        if (isNotBlank(title)) {
            md.addTitle(title);
        }
        if (isNotBlank(magazine.getMagazineTitle())) {
            String publisher = magazine.getMagazineTitle().trim();
            md.addPublisher(publisher);
            md.addAuthor(new Author(publisher));
        }
        md.setLanguage("ja");
        md.addIdentifier(new Identifier(Identifier.Scheme.UUID, "machiiro-" + magazine.getIssueId()));
    }

    private void addIndexResource(Book book, Magazine magazine, List<Page> pages) {
        String content = indexHtmlGenerator.generate(magazine, pages);
        Resource resource = new Resource("index",
                content.getBytes(StandardCharsets.UTF_8), "index.html",
                MediatypeService.XHTML, StandardCharsets.UTF_8.name());
        book.getResources().add(resource);
        book.getSpine().addResource(resource);
    }

    private void addChapters(Book book, List<Page> pages) {
        int index = 1;
        for (Page page : pages) {
            String href = String.format(Locale.ROOT, "chapter_%04d.xhtml", index);
            String id = String.format(Locale.ROOT, "chapter_%04d", index);

            String content = chapterGenerator.generate(index, page);
            Resource resource = new Resource(id,
                    content.getBytes(StandardCharsets.UTF_8), href,
                    MediatypeService.XHTML, StandardCharsets.UTF_8.name());
            book.getResources().add(resource);
            book.getSpine().addResource(resource);
            book.getTableOfContents().addTOCReference(new TOCReference(page.getTitle(), resource));
            index++;
        }
    }

    private void addNavResource(Book book, Magazine magazine, List<Page> pages) {
        String content = navGenerator.generate(bookTitle(magazine), pages);
        Resource resource = new Resource("nav",
                content.getBytes(StandardCharsets.UTF_8), "nav.xhtml",
                MediatypeService.XHTML, StandardCharsets.UTF_8.name());
        resource.setProperties("nav");
        book.getResources().add(resource);
    }

    private Map<Integer, Resource> addImageResources(Book book, List<Page> pages) {
        Map<Integer, Resource> imageResources = new LinkedHashMap<>();
        for (Page page : pages) {
            if (page.getImageData() == null || page.getImageHref() == null) {
                continue;
            }
            MediaType mediaType = MediatypeService.determineMediaType(page.getImageHref());
            if (mediaType == null) {
                mediaType = MediatypeService.PNG;
            }
            Resource resource = new Resource("image_" + page.getPageNo(),
                    page.getImageData(), page.getImageHref(), mediaType, null);
            book.getResources().add(resource);
            imageResources.put(page.getPageNo(), resource);
        }
        return imageResources;
    }

    private void addCover(Book book, Map<Integer, Resource> imageResources) {
        if (imageResources.isEmpty()) {
            return;
        }
        Resource cover = imageResources.get(1);
        if (cover == null) {
            cover = imageResources.values().iterator().next();
        }
        cover.setProperties("cover-image");
        book.setCoverImage(cover);
    }

    private static String bookTitle(Magazine magazine) {
        StringBuilder sb = new StringBuilder();
        if (isNotBlank(magazine.getMagazineTitle())) {
            sb.append(magazine.getMagazineTitle().trim());
        }
        if (isNotBlank(magazine.getTitle())) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(magazine.getTitle().trim());
        }
        return sb.length() > 0 ? sb.toString() : "広報誌";
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
