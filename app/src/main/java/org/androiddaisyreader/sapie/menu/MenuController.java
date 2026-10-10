package org.androiddaisyreader.sapie.menu;

import com.github.library.dodp.exception.DodpException;
import com.github.library.dodp.model.UserResponses;

import org.androiddaisyreader.sapie.SapieDodpClient;
import org.w3c.dom.Element;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * サピエ動的メニュー（BROWSE）の状態遷移を管理するコントローラ。
 *
 * <p>{@code getQuestions} のレスポンス種別（{@link QuestionResult}）に応じて描画側が
 * ビューを切り替える。ナビゲーションは以下:</p>
 * <ul>
 *   <li>{@link #openRoot()}: メインメニュー（{@code questionID=default}）を開く</li>
 *   <li>{@link #select(String)}: 選択肢/入力の questionID を指定して深掘り</li>
 *   <li>{@link #submitInput(String, String)}: 入力質問へ値を送信</li>
 *   <li>{@link #back()}: 1階層戻る（サーバー側 {@code back} と端末側履歴を併用）</li>
 * </ul>
 *
 * <p>予約ID: {@code default}（メインメニュー）/ {@code back}（前の質問）。
 * すべてのメソッドはネットワークI/Oを伴うため、バックグラウンドスレッドから呼ぶこと。</p>
 */
public final class MenuController {

    public static final String QUESTION_DEFAULT = "default";
    public static final String QUESTION_BACK = "back";

    private final SapieDodpClient client;

    // 端末側の遷移履歴（questionID）。ルートは QUESTION_DEFAULT。
    private final Deque<String> history = new ArrayDeque<>();

    public MenuController(SapieDodpClient client) {
        this.client = client;
    }

    /** メインメニューを開く。履歴はリセットされる。 */
    public QuestionResult openRoot() throws DodpException {
        history.clear();
        history.push(QUESTION_DEFAULT);
        return request(UserResponses.of(QUESTION_DEFAULT, null));
    }

    /**
     * 選択肢を選んで遷移する。
     *
     * <p>DODP では multipleChoiceQuestion に答える際、
     * {@code questionID=質問ID（multipleChoiceQuestion の id）}, {@code value=選んだ choice の id}
     * を送る必要がある。</p>
     *
     * @param questionId 現在表示中メニューの質問ID（{@link QuestionResult#getQuestionId()}）
     * @param choiceId   選んだ選択肢の id（{@link Choice#getId()}）
     */
    public QuestionResult select(String questionId, String choiceId) throws DodpException {
        QuestionResult result = request(UserResponses.of(questionId, choiceId));
        // 端末側履歴には「どのメニューで何を選んだか」を積む（back 用）。
        history.push(questionId + "\u0000" + choiceId);
        return result;
    }

    /**
     * 入力質問（inputQuestion）へ値を送信する。
     *
     * @param questionId inputQuestion の id
     * @param value      入力文字（検索語など）
     */
    public QuestionResult submitInput(String questionId, String value) throws DodpException {
        QuestionResult result = request(UserResponses.of(questionId, value));
        history.push(questionId);
        return result;
    }

    /**
     * 1階層戻る。サーバーが serverSideBack 対応のため {@code questionID=back} を送る。
     * 端末側履歴も1つ戻す。履歴がルートのみの場合は戻れない（null を返す）。
     *
     * @return 戻った先のメニュー。これ以上戻れない場合は null。
     */
    public QuestionResult back() throws DodpException {
        if (history.size() <= 1) {
            return null;
        }
        history.pop();
        return request(UserResponses.of(QUESTION_BACK, null));
    }

    /** これ以上戻れるか（ルートより上位があるか）。 */
    public boolean canGoBack() {
        return history.size() > 1;
    }

    private QuestionResult request(UserResponses responses) throws DodpException {
        Element questions = client.getQuestions(responses);
        return QuestionParser.parse(questions);
    }
}
