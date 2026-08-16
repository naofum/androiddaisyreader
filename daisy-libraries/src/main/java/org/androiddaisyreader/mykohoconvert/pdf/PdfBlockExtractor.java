package org.androiddaisyreader.mykohoconvert.pdf;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PDFからブロック（段落・段組み）を判定してテキストを抽出する。
 *
 * <p>各文字（グリフ）の座標を取得し、</p>
 * <ol>
 *   <li>X方向の白い余白（ガッター）から「列（段）」を検出</li>
 *   <li>列ごとにY座標で「行」をまとめる</li>
 *   <li>行間の隙間で「ブロック（段落）」をまとめる</li>
 * </ol>
 * という手順で、読み順（上→下、左→右）に並べたテキストブロックを返す。
 *
 * <p>利用前に {@code com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)} を
 * 呼び出しておくこと。</p>
 */
public class PdfBlockExtractor {

    private static final Logger logger = LoggerFactory.getLogger(PdfBlockExtractor.class);

    private static final float LINE_Y_TOLERANCE_RATIO = 0.5f;
    private static final float LINE_Y_TOLERANCE_MIN = 3.0f;

    /** 単語間スペースとみなす水平ギャップ（フォントのスペース幅に対する比率） */
    private static final float WORD_GAP_RATIO = 0.6f;

    /** 同じブロックとみなす行間の上限（文字サイズに対する比率） */
    private static final float BLOCK_GAP_RATIO = 1.6f;

    /** 列（段）を区切るガッターの最小幅（pt） */
    private static final float GUTTER_MIN = 15.0f;

    private PdfBlockExtractor() {
    }

    /**
     * PDFファイルからページごとのテキストブロックを抽出する。
     * 返却される各ブロックの行は読み順（上→下、左→右）に並べ替え済み。
     *
     * @param pdfFile PDFファイル
     * @return テキストブロックの一覧（page・読み順でソート済み）
     * @throws IOException PDFの読み込みに失敗した場合
     */
    public static List<TextBlock> extract(File pdfFile) throws IOException {
        List<TextBlock> result = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfFile)) {
            int pages = document.getNumberOfPages();
            PdfBoxBlockStripper stripper = new PdfBoxBlockStripper();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                stripper.getText(document);

                List<ColumnRange> columns = detectColumns(stripper.glyphs, stripper.pageWidth, stripper.pageHeight);
                List<RawBlock> blocks = buildBlocks(stripper.glyphs, columns);
                for (RawBlock block : blocks) {
                    TextBlock tb = new TextBlock(page);
                    boolean reverse = isReversed(block);
                    for (int i = 0; i < block.lines.size(); i++) {
                        Line line = reverse
                                ? block.lines.get(block.lines.size() - 1 - i)
                                : block.lines.get(i);
                        tb.addLine(line.text);
                    }
                    result.add(tb);
                }
            }
        }
        logger.info("PDFブロック抽出完了: {}ブロック", result.size());
        return result;
    }

    /**
     * X方向の白い余白（ガッター）から列（段）の範囲を検出する。
     * ヘッダー・フッター領域は判定から除外する。
     */
    private static List<ColumnRange> detectColumns(List<Glyph> glyphs, float pageWidth, float pageHeight) {
        List<ColumnRange> columns = new ArrayList<>();
        if (glyphs.isEmpty() || pageWidth <= 0) {
            return columns;
        }

        float topBand = pageHeight * 0.12f;
        float bottomBand = pageHeight * 0.10f;
        List<Glyph> body = new ArrayList<>();
        for (Glyph g : glyphs) {
            if (g.y >= topBand && g.y <= pageHeight - bottomBand) {
                body.add(g);
            }
        }
        if (body.isEmpty()) {
            body = glyphs;
        }

        List<float[]> intervals = new ArrayList<>();
        for (Glyph g : body) {
            intervals.add(new float[]{g.x, g.x + g.width});
        }
        intervals.sort(Comparator.comparingDouble(a -> a[0]));

        List<float[]> merged = new ArrayList<>();
        for (float[] iv : intervals) {
            if (merged.isEmpty() || iv[0] - merged.get(merged.size() - 1)[1] > 2.0f) {
                merged.add(new float[]{iv[0], iv[1]});
            } else {
                merged.get(merged.size() - 1)[1] = Math.max(merged.get(merged.size() - 1)[1], iv[1]);
            }
        }

        List<Float> boundaries = new ArrayList<>();
        boundaries.add(0f);
        float prevRight = Float.NaN;
        for (float[] iv : merged) {
            if (!Float.isNaN(prevRight) && iv[0] - prevRight >= GUTTER_MIN) {
                boundaries.add((prevRight + iv[0]) / 2f);
            }
            prevRight = iv[1];
        }
        boundaries.add(pageWidth);

        for (int i = 0; i < boundaries.size() - 1; i++) {
            float x0 = boundaries.get(i);
            float x1 = boundaries.get(i + 1);
            if (x1 - x0 > 10f) {
                columns.add(new ColumnRange(x0, x1, columns.size()));
            }
        }
        return columns;
    }

    /**
     * 列ごとにグリフを振り分けて行・ブロックを構築する。
     */
    private static List<RawBlock> buildBlocks(List<Glyph> glyphs, List<ColumnRange> columns) {
        List<RawBlock> blocks = new ArrayList<>();
        List<ColumnRange> effective = columns;
        if (effective.isEmpty()) {
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            for (Glyph g : glyphs) {
                minX = Math.min(minX, g.x);
                maxX = Math.max(maxX, g.x + g.width);
            }
            effective = new ArrayList<>();
            effective.add(new ColumnRange(minX, maxX + 1, 0));
        }

        for (ColumnRange column : effective) {
            List<Glyph> colGlyphs = new ArrayList<>();
            for (Glyph g : glyphs) {
                float center = g.x + g.width / 2f;
                if (center >= column.x0 && center < column.x1) {
                    colGlyphs.add(g);
                }
            }
            List<Line> lines = buildLines(colGlyphs);
            blocks.addAll(mergeBlocks(lines, column.index));
        }

        blocks.sort(Comparator.comparingInt((RawBlock b) -> b.column).thenComparingDouble(b -> b.topY));
        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).index = i + 1;
        }
        return blocks;
    }

    /**
     * グリフをY座標でクラスタリングして行を構築する。
     */
    private static List<Line> buildLines(List<Glyph> glyphs) {
        glyphs.sort(Comparator.comparingDouble((Glyph g) -> g.y).thenComparingDouble(g -> g.x));

        List<Line> lines = new ArrayList<>();
        Line current = null;
        for (Glyph g : glyphs) {
            if (current == null) {
                current = new Line();
                current.add(g);
                continue;
            }
            float tolerance = Math.max(LINE_Y_TOLERANCE_MIN, LINE_Y_TOLERANCE_RATIO * current.maxHeight);
            if (Math.abs(g.y - current.yMean) <= tolerance) {
                current.add(g);
            } else {
                lines.add(current);
                current = new Line();
                current.add(g);
            }
        }
        if (current != null && !current.glyphs.isEmpty()) {
            lines.add(current);
        }

        List<Line> result = new ArrayList<>();
        for (Line line : lines) {
            line.buildText();
            if (!line.text.isEmpty()) {
                result.add(line);
            }
        }
        result.sort(Comparator.comparingDouble((Line l) -> l.topY).thenComparingDouble(l -> l.leftX));
        return result;
    }

    /**
     * 行を縦方向の近接でブロックにまとめる。
     */
    private static List<RawBlock> mergeBlocks(List<Line> lines, int columnIndex) {
        List<RawBlock> blocks = new ArrayList<>();
        RawBlock current = null;
        for (Line line : lines) {
            if (current == null) {
                current = new RawBlock();
                current.add(line);
                continue;
            }
            float gap = line.topY - current.bottomY;
            float maxGap = BLOCK_GAP_RATIO * current.maxFontSize;
            boolean overlap = line.leftX < current.rightX - 3.0f
                    && line.rightX > current.leftX + 3.0f;

            if (gap <= maxGap && overlap) {
                current.add(line);
            } else {
                current.column = columnIndex;
                blocks.add(current);
                current = new RawBlock();
                current.add(line);
            }
        }
        if (current != null && !current.lines.isEmpty()) {
            current.column = columnIndex;
            blocks.add(current);
        }
        return blocks;
    }

    /**
     * ブロック内の行が「下→上」で組版されているかを判定する。
     * タイトル（ブロック内で最大フォント）がブロックの下端にある場合は逆順とみなす。
     */
    private static boolean isReversed(RawBlock block) {
        if (block.lines.size() < 2) {
            return false;
        }
        float maxFont = 0;
        for (Line line : block.lines) {
            maxFont = Math.max(maxFont, line.maxFontSize);
        }
        Line last = block.lines.get(block.lines.size() - 1);
        return last.maxFontSize >= maxFont * 0.95f;
    }

    /** 列（段）のX範囲 */
    private static class ColumnRange {
        final float x0;
        final float x1;
        final int index;

        ColumnRange(float x0, float x1, int index) {
            this.x0 = x0;
            this.x1 = x1;
            this.index = index;
        }
    }

    /** 1文字分の情報 */
    private static class Glyph {
        final String unicode;
        final float x;
        final float y;
        final float width;
        final float fontSize;
        final float height;
        final float spaceWidth;

        Glyph(String unicode, float x, float y, float width, float fontSize, float height, float spaceWidth) {
            this.unicode = unicode;
            this.x = x;
            this.y = y;
            this.width = width;
            this.fontSize = fontSize;
            this.height = height;
            this.spaceWidth = spaceWidth;
        }
    }

    /** 1行分の情報 */
    private static class Line {
        final List<Glyph> glyphs = new ArrayList<>();
        float yMean;
        float maxHeight;
        float maxFontSize;
        float leftX;
        float rightX;
        float topY;
        float bottomY;
        String text = "";

        void add(Glyph g) {
            glyphs.add(g);
            int n = glyphs.size();
            yMean = yMean * (n - 1) / n + g.y / n;
            maxHeight = Math.max(maxHeight, g.height);
            maxFontSize = Math.max(maxFontSize, g.fontSize);
        }

        void buildText() {
            glyphs.sort(Comparator.comparingDouble(g -> g.x));
            StringBuilder sb = new StringBuilder();
            float prevEnd = Float.NaN;
            for (Glyph g : glyphs) {
                if (!Float.isNaN(prevEnd)) {
                    float gap = g.x - prevEnd;
                    float spaceRef = g.spaceWidth > 0 ? g.spaceWidth : g.fontSize * 0.25f;
                    if (gap > WORD_GAP_RATIO * spaceRef) {
                        sb.append(' ');
                    }
                }
                sb.append(g.unicode);
                prevEnd = g.x + g.width;
            }
            text = sb.toString().replaceAll("\\s+", " ").trim();

            leftX = Float.MAX_VALUE;
            rightX = -Float.MAX_VALUE;
            topY = Float.MAX_VALUE;
            bottomY = -Float.MAX_VALUE;
            for (Glyph g : glyphs) {
                leftX = Math.min(leftX, g.x);
                rightX = Math.max(rightX, g.x + g.width);
                topY = Math.min(topY, g.y - g.height);
                bottomY = Math.max(bottomY, g.y);
            }
        }
    }

    /** ブロック（段落・段）の情報 */
    private static class RawBlock {
        final List<Line> lines = new ArrayList<>();
        float leftX = Float.MAX_VALUE;
        float rightX = -Float.MAX_VALUE;
        float topY = Float.MAX_VALUE;
        float bottomY = -Float.MAX_VALUE;
        float maxFontSize;
        int column;
        int index;

        void add(Line line) {
            lines.add(line);
            leftX = Math.min(leftX, line.leftX);
            rightX = Math.max(rightX, line.rightX);
            topY = Math.min(topY, line.topY);
            bottomY = Math.max(bottomY, line.bottomY);
            maxFontSize = Math.max(maxFontSize, line.maxFontSize);
        }
    }

    /** グリフを収集するストライッパ */
    private static class PdfBoxBlockStripper extends PDFTextStripper {
        final List<Glyph> glyphs = new ArrayList<>();
        private float pageWidth;
        private float pageHeight;

        PdfBoxBlockStripper() throws IOException {
            super();
        }

        @Override
        protected void startPage(PDPage page) {
            glyphs.clear();
            pageWidth = page.getMediaBox().getWidth();
            pageHeight = page.getMediaBox().getHeight();
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            for (TextPosition p : positions) {
                if (p.getRotation() != 0) {
                    continue;
                }
                String unicode = p.getUnicode();
                if (unicode == null) {
                    continue;
                }
                float x = p.getXDirAdj();
                float y = pageHeight - p.getYDirAdj();
                float height = Math.abs(p.getHeightDir());
                float spaceWidth = p.getWidthOfSpace();
                glyphs.add(new Glyph(unicode, x, y, p.getWidthDirAdj(), p.getFontSizeInPt(), height, spaceWidth));
            }
        }
    }
}
