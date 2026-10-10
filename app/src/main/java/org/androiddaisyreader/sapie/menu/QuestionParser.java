package org.androiddaisyreader.sapie.menu;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code getQuestions} が返す {@code questions} 要素（raw DOM）を
 * {@link QuestionResult} に変換する固定パーサ。
 *
 * <p>DODP契約に基づく判定のみを行い、サピエ固有のメニュー名・階層はハードコードしない。
 * 名前空間の有無に依存しないよう、要素の localName で判定する。</p>
 *
 * <p>判定優先順位（guidev19.md 5-4/5-5 に準拠）:</p>
 * <ol>
 *   <li>{@code multipleChoiceQuestion} があれば MULTIPLE_CHOICE</li>
 *   <li>{@code inputQuestion} があれば INPUT</li>
 *   <li>{@code contentListRef}（または子孫に id を持つ一覧参照）があれば CONTENT_LIST</li>
 *   <li>いずれも無く {@code text} のみなら TEXT</li>
 * </ol>
 */
public final class QuestionParser {

    private QuestionParser() {
    }

    /**
     * @param questions {@code getQuestions} の戻り値（{@code questions} 要素）。null 可。
     * @return 正規化結果。{@code questions} が null または解釈不能な場合は text(null) を返す。
     */
    public static QuestionResult parse(Element questions) {
        if (questions == null) {
            return QuestionResult.text(null);
        }

        Element multipleChoice = firstChildByLocalName(questions, "multipleChoiceQuestion");
        if (multipleChoice != null) {
            return parseMultipleChoice(multipleChoice);
        }

        Element input = firstChildByLocalName(questions, "inputQuestion");
        if (input != null) {
            return parseInput(input);
        }

        // 図書一覧（終端）: contentListRef 要素、または questions 直下/子孫の contentListRef。
        Element contentListRef = firstDescendantByLocalName(questions, "contentListRef");
        if (contentListRef != null) {
            String ref = textOf(contentListRef);
            if (ref != null && !ref.isEmpty()) {
                return QuestionResult.contentList(ref);
            }
        }
        // 一部実装では questions 直下に id を持つ（＝一覧ID）ケースがある。
        String directId = attr(questions, "id");
        if ((multipleChoice == null && input == null)
                && directId != null && !directId.isEmpty()) {
            return QuestionResult.contentList(directId);
        }

        // メニュー無（text のみ）
        String text = extractText(questions);
        return QuestionResult.text(text);
    }

    private static QuestionResult parseMultipleChoice(Element mcq) {
        String id = attrOrChildText(mcq, "id");
        String text = extractText(mcq);
        List<Choice> choices = new ArrayList<>();
        // サーバーは <multipleChoiceQuestion><choices><choice/></choices> と
        // ラッパー <choices> を挟むため、直接の子ではなく子孫から choice を取得する。
        Element choicesWrapper = firstChildByLocalName(mcq, "choices");
        Element choiceParent = choicesWrapper != null ? choicesWrapper : mcq;
        for (Element choice : childrenByLocalName(choiceParent, "choice")) {
            String cid = attrOrChildText(choice, "id");
            String ctext = extractText(choice);
            choices.add(new Choice(cid, ctext));
        }
        return QuestionResult.multipleChoice(id, text, choices);
    }

    private static QuestionResult parseInput(Element input) {
        String id = attrOrChildText(input, "id");
        String type = attrOrChildText(input, "type");
        String text = extractText(input);
        return QuestionResult.input(id, text, type);
    }

    // ------------------------------------------------------------------
    // text 抽出: <text> 子要素、または <label><text>...</text></label>、
    // または要素直下のテキストノードの順で探す。
    // ------------------------------------------------------------------
    private static String extractText(Element element) {
        Element text = firstChildByLocalName(element, "text");
        if (text != null) {
            String value = textOf(text);
            if (value != null) {
                return value;
            }
        }
        Element label = firstChildByLocalName(element, "label");
        if (label != null) {
            Element labelText = firstChildByLocalName(label, "text");
            if (labelText != null) {
                return textOf(labelText);
            }
            return textOf(label);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // DOM ヘルパー（localName ベース、名前空間非依存）
    // ------------------------------------------------------------------

    private static String localName(Node node) {
        String ln = node.getLocalName();
        if (ln != null) {
            return ln;
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static Element firstChildByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(localName(node))) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> childrenByLocalName(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(localName(node))) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static Element firstDescendantByLocalName(Element root, String localName) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            if (localName.equals(localName(element))) {
                return element;
            }
            Element found = firstDescendantByLocalName(element, localName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 属性値、無ければ同名の子要素テキストを返す。 */
    private static String attrOrChildText(Element element, String name) {
        String value = attr(element, name);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        Element child = firstChildByLocalName(element, name);
        return child != null ? textOf(child) : value;
    }

    private static String attr(Element element, String name) {
        if (element.hasAttribute(name)) {
            String value = element.getAttribute(name);
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private static String textOf(Element element) {
        String content = element.getTextContent();
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
