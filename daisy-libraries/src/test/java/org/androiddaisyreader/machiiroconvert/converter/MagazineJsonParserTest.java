package org.androiddaisyreader.machiiroconvert.converter;

import org.androiddaisyreader.machiiroconvert.model.Magazine;
import org.androiddaisyreader.machiiroconvert.model.MagazineDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MagazineJsonParserTest {

    private final MagazineJsonParser parser = new MagazineJsonParser();

    @Test
    void parse_buildsMagazine() {
        String json = "{"
                + "\"data\": {"
                + "  \"magazineIssueFile\": {"
                + "    \"magazine_issue\": {"
                + "      \"title\": \"令和8年 8月11日号\","
                + "      \"magazine_title\": \"なかの 区報\""
                + "    }"
                + "  },"
                + "  \"documents\": ["
                + "    {\"page_no\": 2, \"block_info\": {\"positionYfrom\": 100, \"positionXfrom\": 20}, \"id\": 2, \"document_text\": \"二ページ 目\"},"
                + "    {\"page_no\": 1, \"block_info\": {\"positionYfrom\": 200, \"positionXfrom\": 10}, \"id\": 1, \"document_text\": \"一ページ 目\"}"
                + "  ],"
                + "  \"htmlUrl\": \"https://example.com/html\","
                + "  \"pdfUrl\": \"https://example.com/pdf\","
                + "  \"pages\": 4"
                + "}"
                + "}";

        Magazine magazine = parser.parse("issue-1", json);

        assertEquals("issue-1", magazine.getIssueId());
        assertEquals("令和8年8月11日号", magazine.getTitle());
        assertEquals("なかの区報", magazine.getMagazineTitle());
        assertEquals("https://example.com/html", magazine.getHtmlUrl());
        assertEquals("https://example.com/pdf", magazine.getPdfUrl());
        assertEquals(4, magazine.getPages());
    }

    @Test
    void parse_sortsDocumentsByPageThenPosition() {
        String json = "{"
                + "\"data\": {"
                + "  \"magazineIssueFile\": {\"magazine_issue\": {\"title\": \"1号\", \"magazine_title\": \"誌\"}},"
                + "  \"documents\": ["
                + "    {\"page_no\": 1, \"block_info\": {\"positionYfrom\": 300, \"positionXfrom\": 0}, \"id\": 3, \"document_text\": \"下\"},"
                + "    {\"page_no\": 1, \"block_info\": {\"positionYfrom\": 100, \"positionXfrom\": 0}, \"id\": 1, \"document_text\": \"上\"},"
                + "    {\"page_no\": 2, \"block_info\": {\"positionYfrom\": 100, \"positionXfrom\": 0}, \"id\": 2, \"document_text\": \"次ページ\"}"
                + "  ]"
                + "}"
                + "}";

        Magazine magazine = parser.parse("issue-1", json);
        List<MagazineDocument> documents = magazine.getDocuments();

        assertEquals(3, documents.size());
        assertEquals(1, documents.get(0).getId()); // 1ページ目・上
        assertEquals(3, documents.get(1).getId()); // 1ページ目・下
        assertEquals(2, documents.get(2).getId()); // 2ページ目
    }

    @Test
    void parse_handlesMissingData() {
        String json = "{}";

        Magazine magazine = parser.parse("issue-1", json);

        assertEquals("issue-1", magazine.getIssueId());
        assertEquals("", magazine.getTitle());
        assertEquals("", magazine.getMagazineTitle());
        assertEquals(0, magazine.getDocuments().size());
        assertEquals("", magazine.getHtmlUrl());
        assertEquals("", magazine.getPdfUrl());
        assertEquals(0, magazine.getPages());
    }
}
