package org.androiddaisyreader.sapie.menu;

/**
 * multipleChoiceQuestion の選択肢。{@code id} を次の questionID として
 * {@code getQuestions} に渡す。
 */
public final class Choice {

    private final String id;
    private final String text;

    public Choice(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }
}
