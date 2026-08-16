package org.androiddaisyreader.machiiroconvert.converter;

import org.androiddaisyreader.machiiroconvert.model.Magazine;
import org.androiddaisyreader.machiiroconvert.model.MagazineDocument;
import org.androiddaisyreader.machiiroconvert.util.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * マチイロのテキストデータAPIが返すJSONを解析して {@link Magazine} を構築する。
 *
 * <p>対応するJSON構造（仕様より）:
 * <pre>
 * data -> magazineIssueFile -> magazine_issue -> title
 * data -> magazineIssueFile -> magazine_issue -> magazine_title
 * data -> documents -> array
 *   page_no
 *   block_info -> positionYfrom, positionXfrom
 *   id
 *   document_text
 * data -> htmlUrl
 * data -> pdfUrl
 * data -> pages
 * </pre>
 */
public class MagazineJsonParser {

    /**
     * JSON文字列を解析してMagazineを構築する。
     *
     * @param issueId 発行ID
     * @param json    APIレスポンスのJSON文字列
     * @return 構築されたMagazine
     */
    public Magazine parse(String issueId, String json) {
        Object root = JsonParser.parse(json);
        Map<String, Object> rootMap = asObject(root);
        Map<String, Object> data = asObject(get(rootMap, "data"));

        Map<String, Object> magazineIssueFile = asObject(get(data, "magazineIssueFile"));
        Map<String, Object> magazineIssue = asObject(get(magazineIssueFile, "magazine_issue"));
        String title = asString(get(magazineIssue, "title")).replace(" ", "");
        String magazineTitle = asString(get(magazineIssue, "magazine_title")).replace(" ", "");

        List<MagazineDocument> documents = parseDocuments(get(data, "documents"));

        String htmlUrl = firstString(get(data, "htmlUrl"), get(rootMap, "htmlUrl"));
        String pdfUrl = firstString(get(data, "pdfUrl"), get(rootMap, "pdfUrl"));
        int pages = asInt(first(get(data, "pages"), get(rootMap, "pages")));

        return new Magazine(issueId, title, magazineTitle, documents, htmlUrl, pdfUrl, pages);
    }

    /**
     * documents 配列を解析し、ページ番号 → 縦位置 → 横位置 の順（上から下、左から右）で
     * ソートした MagazinDocument のリストを返す。
     */
    private List<MagazineDocument> parseDocuments(Object documentsObj) {
        List<MagazineDocument> result = new ArrayList<>();
        if (documentsObj == null) {
            return result;
        }
        for (Object o : asList(documentsObj)) {
            Map<String, Object> doc = asObject(o);
            int pageNo = asInt(doc.get("page_no"));
            Map<String, Object> blockInfo = asObject(doc.get("block_info"));
            double positionYfrom = asNumber(blockInfo.get("positionYfrom"));
            double positionXfrom = asNumber(blockInfo.get("positionXfrom"));
            int id = asInt(doc.get("id"));
            String documentText = asString(doc.get("document_text")).replace(" ", "");
            result.add(new MagazineDocument(pageNo, positionYfrom, positionXfrom, id, documentText));
        }
        result.sort(Comparator.comparingInt(MagazineDocument::getPageNo)
                .thenComparingDouble(MagazineDocument::getPositionYfrom)
                .thenComparingDouble(MagazineDocument::getPositionXfrom));
        return result;
    }

    private static Object get(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }

    private static Object first(Object a, Object b) {
        return a != null ? a : b;
    }

    private static String firstString(Object a, Object b) {
        String sa = asString(a);
        if (!sa.isEmpty()) {
            return sa;
        }
        return asString(b);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object o) {
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        if (o instanceof List) {
            return (List<Object>) o;
        }
        return new ArrayList<>();
    }

    private static String asString(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof String) {
            return (String) o;
        }
        return String.valueOf(o);
    }

    private static double asNumber(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o instanceof String) {
            try {
                return Double.parseDouble((String) o);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static int asInt(Object o) {
        return (int) Math.round(asNumber(o));
    }
}
