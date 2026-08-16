package org.androiddaisyreader.machiiroconvert.converter;

import org.androiddaisyreader.machiiroconvert.exception.MachiiroConvertException;
import org.androiddaisyreader.machiiroconvert.model.Magazine;
import org.androiddaisyreader.machiiroconvert.model.MagazineDocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * マチイロの広報誌（Magazineとページ画像）をEPUBファイルへ変換する。
 * <p>
 * 使い方:
 * <pre>
 *   new MachiiroEpubConverter().convert(magazine, pageImages, outputFile);
 * </pre>
 */
public class MachiiroEpubConverter {

    private static final int TITLE_MAX_LENGTH = 40;

    private final EpubAssembler epubAssembler = new EpubAssembler();

    /**
     * 広報誌データとページ画像をEPUBファイルへ変換して出力する。
     *
     * @param magazine    広報誌データ
     * @param pageImages  ページ画像一覧（htmlUrlから取得したもの）
     * @param outputFile  出力EPUBファイル
     * @throws MachiiroConvertException 変換失敗時
     * @throws IOException              入出力エラー時
     */
    public void convert(Magazine magazine, List<PageImage> pageImages, File outputFile)
            throws MachiiroConvertException, IOException {
        if (magazine == null) {
            throw new MachiiroConvertException("広報誌データがnullです");
        }
        List<Page> pages = buildPages(magazine, pageImages);
        if (pages.isEmpty()) {
            throw new MachiiroConvertException("変換対象のページがありません");
        }
        epubAssembler.assemble(magazine, pages, outputFile);
    }

    /**
     * 広報誌データとページ画像から、ページ単位のモデルを構築する。
     * テキストは documents（既にソート済み）をページ番号でまとめて割り当てる。
     */
    private List<Page> buildPages(Magazine magazine, List<PageImage> pageImages) {
        Map<Integer, List<String>> textsByPage = new LinkedHashMap<>();
        Map<Integer, List<Integer>> idsByPage = new LinkedHashMap<>();
        if (magazine.getDocuments() != null) {
            for (MagazineDocument document : magazine.getDocuments()) {
                idsByPage.computeIfAbsent(document.getPageNo(), k -> new ArrayList<>())
                        .add(document.getId());
                textsByPage.computeIfAbsent(document.getPageNo(), k -> new ArrayList<>())
                        .add(document.getDocumentText());
            }
        }

        Map<Integer, PageImage> imageByPage = new HashMap<>();
        if (pageImages != null) {
            for (PageImage image : pageImages) {
                imageByPage.put(image.getPageNo(), image);
            }
        }

        int totalPages = magazine.getPages();
        for (int pageNo : textsByPage.keySet()) {
            totalPages = Math.max(totalPages, pageNo);
        }
        for (int pageNo : imageByPage.keySet()) {
            totalPages = Math.max(totalPages, pageNo);
        }
        if (totalPages <= 0) {
            totalPages = textsByPage.size();
        }

        List<Page> pages = new ArrayList<>();
        for (int pageNo = 1; pageNo <= totalPages; pageNo++) {
            List<Integer> ids = idsByPage.getOrDefault(pageNo, Collections.emptyList());
            List<String> texts = textsByPage.getOrDefault(pageNo, Collections.emptyList());
            PageImage image = imageByPage.get(pageNo);
            String title = deriveTitle(texts, pageNo);
            pages.add(new Page(pageNo, title, ids, texts,
                    image != null ? image.getHref() : null,
                    image != null ? image.getData() : null));
        }
        return pages;
    }

    /**
     * ページの見出しを決定する。最初のテキストブロックを短縮して使用し、
     * 無い場合は「ページN」とする。
     */
    private static String deriveTitle(List<String> texts, int pageNo) {
        for (String text : texts) {
            String trimmed = text == null ? "" : text.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > TITLE_MAX_LENGTH) {
                    trimmed = trimmed.substring(0, TITLE_MAX_LENGTH) + "…";
                }
                return trimmed;
            }
        }
        return "ページ" + pageNo;
    }
}
