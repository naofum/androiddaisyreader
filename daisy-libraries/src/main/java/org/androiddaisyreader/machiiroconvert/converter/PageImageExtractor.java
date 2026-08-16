package org.androiddaisyreader.machiiroconvert.converter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * htmlUrlで取得したHTMLから、ページごとの画像バイナリを抽出する。
 *
 * <p>対象HTMLは次の形式を想定する。
 * <pre>
 * &lt;div class="page image-container" data-page="1"&gt;
 *   &lt;img src="data:image/png;base64,...."&gt;
 * &lt;/div&gt;
 * </pre>
 */
public class PageImageExtractor {

    /**
     * HTMLを解析して、ページごとの画像を抽出する。ページ番号順にソートして返す。
     *
     * @param html htmlUrlのレスポンス（HTML）
     * @return 抽出したPageImageのリスト
     */
    public List<PageImage> extract(String html) {
        List<PageImage> images = new ArrayList<>();
        if (html == null) {
            return images;
        }

        Document doc = Jsoup.parse(html);
        Elements pageDivs = doc.select("div.page[data-page]");
        for (Element pageDiv : pageDivs) {
            int pageNo;
            try {
                pageNo = Integer.parseInt(pageDiv.attr("data-page"));
            } catch (NumberFormatException e) {
                continue;
            }
            Element img = pageDiv.selectFirst("img[src]");
            if (img == null) {
                continue;
            }
            PageImage image = parseDataUri(pageNo, img.attr("src"));
            if (image != null) {
                images.add(image);
            }
        }

        images.sort(Comparator.comparingInt(PageImage::getPageNo));
        return images;
    }

    /**
     * data:image/... 形式のURIからPageImageを構築する。
     */
    private PageImage parseDataUri(int pageNo, String src) {
        if (src == null || !src.startsWith("data:")) {
            return null;
        }
        int comma = src.indexOf(',');
        if (comma < 0) {
            return null;
        }
        String header = src.substring("data:".length(), comma);
        String payload = src.substring(comma + 1);

        boolean isBase64 = header.endsWith(";base64");
        String mimeType = header;
        if (isBase64) {
            mimeType = header.substring(0, header.length() - ";base64".length());
        } else {
            int semi = header.indexOf(';');
            if (semi >= 0) {
                mimeType = header.substring(0, semi);
            }
        }
        mimeType = mimeType.toLowerCase(Locale.ROOT);

        String extension = extensionFromMime(mimeType);
        if (extension == null) {
            return null;
        }

        byte[] data;
        try {
            data = isBase64
                    ? Base64.getDecoder().decode(payload)
                    : payload.getBytes(StandardCharsets.ISO_8859_1);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new PageImage(pageNo, data, extension);
    }

    private String extensionFromMime(String mimeType) {
        switch (mimeType) {
            case "image/png":
                return "png";
            case "image/jpeg":
            case "image/jpg":
                return "jpg";
            case "image/gif":
                return "gif";
            case "image/webp":
                return "webp";
            case "image/svg+xml":
                return "svg";
            default:
                return null;
        }
    }
}
