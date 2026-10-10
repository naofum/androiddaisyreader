package org.androiddaisyreader.sapie.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * {@link QuestionParser} の分岐網羅テスト。
 * getQuestions レスポンス（questions 要素）の3種＋text-only、
 * および名前空間あり/なし・label/text ネストを検証する。
 */
public class QuestionParserTest {

    private Element parseXmlToQuestions(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement();
    }

    @Test
    public void nullQuestionsReturnsTextWithNull() {
        QuestionResult result = QuestionParser.parse(null);
        assertEquals(QuestionResult.Type.TEXT, result.getType());
        assertNull(result.getText());
    }

    @Test
    public void multipleChoiceWithChoices() throws Exception {
        String xml =
                "<questions>"
                        + "  <multipleChoiceQuestion id=\"q1\">"
                        + "    <text>メインメニュー</text>"
                        + "    <choice id=\"c1\"><text>図書検索</text></choice>"
                        + "    <choice id=\"c2\"><text>ネット閲覧室</text></choice>"
                        + "  </multipleChoiceQuestion>"
                        + "</questions>";
        QuestionResult result = QuestionParser.parse(parseXmlToQuestions(xml));

        assertEquals(QuestionResult.Type.MULTIPLE_CHOICE, result.getType());
        assertEquals("q1", result.getQuestionId());
        assertEquals("メインメニュー", result.getText());
        assertEquals(2, result.getChoices().size());
        assertEquals("c1", result.getChoices().get(0).getId());
        assertEquals("図書検索", result.getChoices().get(0).getText());
        assertEquals("c2", result.getChoices().get(1).getId());
        assertEquals("ネット閲覧室", result.getChoices().get(1).getText());
    }

    @Test
    public void multipleChoiceWithChoicesWrapper() throws Exception {
        // 実サーバー（サピエ）の構造: choice は <choices> ラッパーの中にある。
        // xml:lang 付き label/text も実際の形に合わせる。
        String xml =
                "<questions xmlns=\"http://www.daisy.org/ns/daisy-online/\">"
                        + "  <multipleChoiceQuestion id=\"root\">"
                        + "    <label xml:lang=\"ja\"><text>サピエへようこそ。</text></label>"
                        + "    <choices>"
                        + "      <choice id=\"continue_contentlist\"><label xml:lang=\"ja\"><text>前回の検索結果一覧</text></label></choice>"
                        + "      <choice id=\"book\"><label xml:lang=\"ja\"><text>図書検索</text></label></choice>"
                        + "      <choice id=\"magazine\"><label xml:lang=\"ja\"><text>雑誌検索</text></label></choice>"
                        + "    </choices>"
                        + "  </multipleChoiceQuestion>"
                        + "</questions>";
        QuestionResult result = QuestionParser.parse(parseXmlToQuestions(xml));

        assertEquals(QuestionResult.Type.MULTIPLE_CHOICE, result.getType());
        assertEquals("root", result.getQuestionId());
        assertEquals("サピエへようこそ。", result.getText());
        assertEquals(3, result.getChoices().size());
        assertEquals("continue_contentlist", result.getChoices().get(0).getId());
        assertEquals("前回の検索結果一覧", result.getChoices().get(0).getText());
        assertEquals("book", result.getChoices().get(1).getId());
        assertEquals("図書検索", result.getChoices().get(1).getText());
        assertEquals("magazine", result.getChoices().get(2).getId());
    }

    @Test
    public void inputQuestion() throws Exception {
        String xml =
                "<questions>"
                        + "  <inputQuestion id=\"q2\">"
                        + "    <type>TEXT_ALPHANUMERIC</type>"
                        + "    <text>検索語を入力</text>"
                        + "  </inputQuestion>"
                        + "</questions>";
        QuestionResult result = QuestionParser.parse(parseXmlToQuestions(xml));

        assertEquals(QuestionResult.Type.INPUT, result.getType());
        assertEquals("q2", result.getQuestionId());
        assertEquals("検索語を入力", result.getText());
        assertEquals("TEXT_ALPHANUMERIC", result.getInputType());
    }

    @Test
    public void inputQuestionWithTypeAsAttribute() throws Exception {
        String xml =
                "<questions>"
                        + "  <inputQuestion id=\"q2\" type=\"TEXT_ALPHANUMERIC\">"
                        + "    <text>検索語</text>"
                        + "  </inputQuestion>"
                        + "</questions>";
        QuestionResult result = QuestionParser.parse(parseXmlToQuestions(xml));

        assertEquals(QuestionResult.Type.INPUT, result.getType());
        assertEquals("TEXT_ALPHANUMERIC", result.getInputType());
    }

    @Test
    public void contentListRefTerminal() throws Exception {
        String xml =
                "<questions>"
                        + "  <contentListRef>search-result-123</contentListRef>"
                        + "</questions>";
        QuestionResult result = QuestionParser.parse(parseXmlToQuestions(xml));

        assertEquals(QuestionResult.Type.CONTENT_LIST, result.getType());
        assertEquals("search-result-123", result.getContentListRef());
    }

    @Test
    public void textOnlyMenu() throws Exception {
        String xml =
                "<questions>"
                        + "  <text>該当する図書がありません</text>"
                        + "</questions>";
        QuestionResult result = QuestionParser.parse(parseXmlToQuestions(xml));

        assertEquals(QuestionResult.Type.TEXT, result.getType());
        assertEquals("該当する図書がありません", result.getText());
    }

    @Test
    public void labelTextNesting() throws Exception {
        // text が label/text にネストされているケース
        String xml =
                "<questions>"
                        + "  <multipleChoiceQuestion id=\"q1\">"
                        + "    <label><text>ジャンル選択</text></label>"
                        + "    <choice id=\"c1\"><label><text>文学</text></label></choice>"
                        + "  </multipleChoiceQuestion>"
                        + "</questions>";
        QuestionResult result = QuestionParser.parse(parseXmlToQuestions(xml));

        assertEquals(QuestionResult.Type.MULTIPLE_CHOICE, result.getType());
        assertEquals("ジャンル選択", result.getText());
        assertEquals(1, result.getChoices().size());
        assertEquals("文学", result.getChoices().get(0).getText());
    }

    @Test
    public void namespacedElements() throws Exception {
        // DAISY Online 名前空間付きでも localName で判定できること
        String xml =
                "<d:questions xmlns:d=\"http://www.daisy.org/ns/daisy-online/\">"
                        + "  <d:multipleChoiceQuestion id=\"q1\">"
                        + "    <d:text>メニュー</d:text>"
                        + "    <d:choice id=\"c1\"><d:text>新着</d:text></d:choice>"
                        + "  </d:multipleChoiceQuestion>"
                        + "</d:questions>";
        QuestionResult result = QuestionParser.parse(parseXmlToQuestions(xml));

        assertEquals(QuestionResult.Type.MULTIPLE_CHOICE, result.getType());
        assertEquals("メニュー", result.getText());
        assertEquals(1, result.getChoices().size());
        assertEquals("c1", result.getChoices().get(0).getId());
        assertEquals("新着", result.getChoices().get(0).getText());
    }

    @Test
    public void multipleChoiceTakesPrecedenceOverText() throws Exception {
        // multipleChoiceQuestion と text が同居しても MULTIPLE_CHOICE と判定
        String xml =
                "<questions>"
                        + "  <text>案内文</text>"
                        + "  <multipleChoiceQuestion id=\"q1\">"
                        + "    <text>選択してください</text>"
                        + "    <choice id=\"c1\"><text>A</text></choice>"
                        + "  </multipleChoiceQuestion>"
                        + "</questions>";
        QuestionResult result = QuestionParser.parse(parseXmlToQuestions(xml));

        assertEquals(QuestionResult.Type.MULTIPLE_CHOICE, result.getType());
        assertNotNull(result.getChoices());
        assertTrue(result.getChoices().size() >= 1);
    }
}
