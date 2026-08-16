package org.androiddaisyreader.mykohoconvert.pdf;

import android.graphics.Bitmap;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PDFの各ページを低DPIのJPEG画像としてレンダリングする。
 */
public class PdfPageRenderer {

    private static final Logger logger = LoggerFactory.getLogger(PdfPageRenderer.class);

    /** JPEG圧縮品質（0-100）。BuildPageEpubの0.75に相当。 */
    private static final int JPEG_QUALITY = 75;

    private PdfPageRenderer() {
    }

    /**
     * PDFの各ページを低DPIのJPEG画像としてレンダリングする。
     *
     * @param pdfFile PDFファイル
     * @param dpi     解像度（例: 72）
     * @return ページ番号（1始まり）→ JPEG画像バイト列 のマップ
     * @throws IOException レンダリングに失敗した場合
     */
    public static Map<Integer, byte[]> render(File pdfFile, int dpi) throws IOException {
        Map<Integer, byte[]> images = new LinkedHashMap<>();
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = document.getNumberOfPages();
            for (int i = 0; i < pages; i++) {
                Bitmap bitmap = renderer.renderImageWithDPI(i, dpi);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
                images.put(i + 1, baos.toByteArray());
                bitmap.recycle();
            }
        }
        logger.info("PDFページ画像のレンダリング完了: {}ページ", images.size());
        return images;
    }
}
