package org.androiddaisyreader.sapie.menu;

import java.util.Collections;
import java.util.List;

/**
 * {@code getQuestions} レスポンス（{@code questions} 要素）を種別ごとに正規化した結果。
 *
 * <p>DODP契約（サピエDAISY Online 利用ガイド）に基づき、レスポンスは以下の種別のいずれか:</p>
 * <ul>
 *   <li>{@link Type#MULTIPLE_CHOICE}: 選択肢の質問（id/text + choice+）</li>
 *   <li>{@link Type#INPUT}: テキスト入力の質問（id/type/text）</li>
 *   <li>{@link Type#CONTENT_LIST}: 図書一覧（contentListRef）＝メニュー終端</li>
 *   <li>{@link Type#TEXT}: メニュー無（text のみ）</li>
 * </ul>
 */
public final class QuestionResult {

    public enum Type {
        MULTIPLE_CHOICE,
        INPUT,
        CONTENT_LIST,
        TEXT
    }

    private final Type type;
    private final String questionId;     // MULTIPLE_CHOICE / INPUT
    private final String text;           // すべての種別で表示・読み上げに使用（無い場合 null）
    private final List<Choice> choices;  // MULTIPLE_CHOICE
    private final String inputType;      // INPUT（例: TEXT_ALPHANUMERIC）
    private final String contentListRef; // CONTENT_LIST

    private QuestionResult(Type type, String questionId, String text, List<Choice> choices,
                           String inputType, String contentListRef) {
        this.type = type;
        this.questionId = questionId;
        this.text = text;
        this.choices = choices == null ? Collections.emptyList() : choices;
        this.inputType = inputType;
        this.contentListRef = contentListRef;
    }

    public static QuestionResult multipleChoice(String questionId, String text, List<Choice> choices) {
        return new QuestionResult(Type.MULTIPLE_CHOICE, questionId, text, choices, null, null);
    }

    public static QuestionResult input(String questionId, String text, String inputType) {
        return new QuestionResult(Type.INPUT, questionId, text, null, inputType, null);
    }

    public static QuestionResult contentList(String contentListRef) {
        return new QuestionResult(Type.CONTENT_LIST, null, null, null, null, contentListRef);
    }

    public static QuestionResult text(String text) {
        return new QuestionResult(Type.TEXT, null, text, null, null, null);
    }

    public Type getType() {
        return type;
    }

    public String getQuestionId() {
        return questionId;
    }

    public String getText() {
        return text;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public String getInputType() {
        return inputType;
    }

    public String getContentListRef() {
        return contentListRef;
    }
}
