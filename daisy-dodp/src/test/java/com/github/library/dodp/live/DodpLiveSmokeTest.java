package com.github.library.dodp.live;

import com.github.library.dodp.client.DodpClient;
import com.github.library.dodp.client.DodpConfiguration;
import com.github.library.dodp.model.ContentItem;
import com.github.library.dodp.model.ContentList;
import com.github.library.dodp.model.ContentMetadata;
import com.github.library.dodp.model.InputType;
import com.github.library.dodp.model.Label;
import com.github.library.dodp.model.OptionalOperation;
import com.github.library.dodp.model.ReadingSystemAttributes;
import com.github.library.dodp.model.ServiceAttributes;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/**
 * サピエ DAISY Online サービス（DODP）に対する「実接続」スモークテスト。
 *
 * <p>読み取り系の操作のみを順に実行し、想定どおり動作するかを確認する。
 * サーバー状態を変更する操作（issueContent / returnContent / setBookmarks など）は
 * 一切呼び出さない。</p>
 *
 * <p>認証情報は環境変数で供給する（ソース・ログに資格情報を残さないため）:</p>
 * <ul>
 *   <li>{@code SAPIE_DODP_USERNAME} … 検証用サピエID（必須）</li>
 *   <li>{@code SAPIE_DODP_PASSWORD} … パスワード（必須）</li>
 *   <li>{@code SAPIE_DODP_ENDPOINT} … エンドポイント（任意。既定は本番 ptx）</li>
 * </ul>
 *
 * <p>環境変数が未設定の場合、テストは失敗ではなく「スキップ」される。</p>
 */
@DisplayName("DODP live smoke test (read-only)")
class DodpLiveSmokeTest {

    private static final String DEFAULT_ENDPOINT =
            "https://ptx.sapie.or.jp/DaisyOnlineService/DaisyOnlineService";

    @Test
    @DisplayName("logOn → getServiceAttributes → setReadingSystemAttributes → getContentList → logOff")
    void readOnlyFlow() throws Exception {
        String username = System.getenv("SAPIE_DODP_USERNAME");
        String password = System.getenv("SAPIE_DODP_PASSWORD");
        String endpoint = System.getenv("SAPIE_DODP_ENDPOINT");
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = DEFAULT_ENDPOINT;
        }

        Assumptions.assumeTrue(username != null && !username.isEmpty(),
                "SAPIE_DODP_USERNAME 未設定のためスキップ");
        Assumptions.assumeTrue(password != null && !password.isEmpty(),
                "SAPIE_DODP_PASSWORD 未設定のためスキップ");

        System.out.println("[DODP] endpoint = " + endpoint);

        DodpConfiguration config = DodpConfiguration.builder(endpoint).build();
        try (DodpClient client = new DodpClient(config)) {

            // 1. ログオン
            boolean loggedIn = client.logOn(username, password);
            System.out.println("[DODP] logOn = " + loggedIn);
            org.junit.jupiter.api.Assertions.assertTrue(loggedIn, "logOn が false を返しました（ID/パスワードを確認）");

            // 2. サービス属性
            ServiceAttributes service = client.getServiceAttributes();
            System.out.println("[DODP] getServiceAttributes:");
            printServiceAttributes(service);
            org.junit.jupiter.api.Assertions.assertNotNull(service, "serviceAttributes が null");

            // 3. 端末情報送信（サピエ: 日本語UI + DAISYフォーマット）
            boolean rsOk = client.setReadingSystemAttributes(buildReadingSystemAttributes());
            System.out.println("[DODP] setReadingSystemAttributes = " + rsOk);
            org.junit.jupiter.api.Assertions.assertTrue(rsOk, "setReadingSystemAttributes が false");

            // 4. コンテンツ一覧（予約ラベル: new / issued / expired）読み取りのみ
            dumpContentList(client, ContentList.LIST_NEW);
            dumpContentList(client, ContentList.LIST_ISSUED);
            dumpContentList(client, ContentList.LIST_EXPIRED);

            // 4b. 書庫（動的メニュー）: getQuestions(default) からメニューをたどり、
            //     図書一覧に到達したら getContentList で件数を取得する（読み取りのみ）。
            browseBookshelfCounts(client);

            // 4c. 図書検索（文字入力）→ 検索結果件数（読み取りのみ・ダウンロードはしない）。
            //     SAPIE_DODP_KEYWORD 環境変数で検索語を指定（未設定なら "本"）。
            searchBooksCount(client);

            // 5. ログオフ
            boolean loggedOff = client.logOff();
            System.out.println("[DODP] logOff = " + loggedOff);
            org.junit.jupiter.api.Assertions.assertTrue(loggedOff, "logOff が false");
        }
    }

    // ------------------------------------------------------------------
    // 書庫（動的メニュー）探索: getQuestions(default) から辿り、
    // 図書一覧（questions に id 属性 または contentListRef）へ到達したら
    // getContentList で totalItems（件数）を取得する。件数確認のため
    // 深さ優先で各選択肢を辿る（訪問済み questionID はスキップして循環防止）。
    // 読み取り専用（issue/return/bookmark は呼ばない）。
    // ------------------------------------------------------------------
    private static void browseBookshelfCounts(DodpClient client) {
        System.out.println("[DODP] 書庫メニュー探索を開始（getQuestions=default）");
        try {
            // 1) メインメニュー（root）を取得
            Element root = client.getQuestions(
                    com.github.library.dodp.model.UserResponses.of("default", null));
            System.out.println("[DODP] --- getQuestions(default) 生XML ---");
            System.out.println(root == null ? "(null)"
                    : com.github.library.dodp.util.XmlUtil.serialize(root));
            System.out.println("[DODP] --- 生XML ここまで ---");
            if (root == null) {
                return;
            }

            Element mcq = firstDescendantByLocalName(root, "multipleChoiceQuestion");
            if (mcq == null) {
                System.out.println("[DODP] ルートに multipleChoiceQuestion なし");
                return;
            }
            String rootId = attrOrChildText(mcq, "id"); // 例: "root"

            // 2) 各選択肢を 1 階層だけ probe する（深い再帰はしない＝ハング回避）。
            //    DODP の正しい送り方: questionID=rootId, value=choiceId
            int listCount = 0;
            for (Element choice : descendantsByLocalName(mcq, "choice")) {
                String choiceId = attrOrChildText(choice, "id");
                String choiceText = extractMenuText(choice);
                if (choiceId == null || choiceId.isEmpty()) {
                    continue;
                }
                System.out.println("           -> choice " + choiceId + " (" + nz(choiceText) + ")");
                Element next = client.getQuestions(
                        com.github.library.dodp.model.UserResponses.of(rootId, choiceId));
                System.out.println("              [raw " + rootId + "=" + choiceId + "] "
                        + (next == null ? "(null)"
                           : com.github.library.dodp.util.XmlUtil.serialize(next)));
                if (next == null) {
                    continue;
                }
                String listId = contentListId(next);
                if (listId != null) {
                    reportContentListCount(client, listId, 3);
                    listCount++;
                } else if (firstDescendantByLocalName(next, "inputQuestion") != null) {
                    System.out.println("              -> 入力質問（キーワード検索が必要）");
                } else if (firstDescendantByLocalName(next, "multipleChoiceQuestion") != null) {
                    System.out.println("              -> さらにサブメニュー（この probe では展開しない）");
                } else {
                    System.out.println("              -> 空応答またはテキストのみ");
                }
                // 選択肢を1つ試すたびにメインメニューへ戻す（サーバー状態をリセット）
                client.getQuestions(com.github.library.dodp.model.UserResponses.of("back", null));
            }
            System.out.println("[DODP] 書庫メニュー探索を終了。件数取得できた図書一覧の数 = " + listCount);
        } catch (Exception e) {
            System.out.println("[DODP] 書庫メニュー探索でエラー: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 図書の文字入力検索 → 検索結果件数（読み取りのみ）。
    // root -> book(図書検索) -> book_search(文字入力検索) -> inputQuestion にキーワード送信
    // -> 検索結果一覧（contentListRef/id）に到達したら getContentList で totalItems を取得。
    // ステップ数に上限を設けてハングを防止する。
    // ------------------------------------------------------------------
    private static void searchBooksCount(DodpClient client) {
        String keyword = System.getenv("SAPIE_DODP_KEYWORD");
        if (keyword == null || keyword.isEmpty()) {
            keyword = "本";
        }
        System.out.println("[DODP] 図書 文字入力検索を開始（keyword=\"" + keyword + "\"）");
        try {
            // 1) root -> book
            Element q = client.getQuestions(
                    com.github.library.dodp.model.UserResponses.of("root", "book"));
            // 2) book -> book_search（文字入力検索）
            String bookMenuId = menuId(q); // 期待値 "book"
            if (bookMenuId == null) {
                System.out.println("           図書検索メニューが取得できませんでした");
                return;
            }
            q = client.getQuestions(
                    com.github.library.dodp.model.UserResponses.of(bookMenuId, "book_search"));

            // 3) 入力質問をたどってキーワードを送る（最大6ステップ）
            int steps = 0;
            while (q != null && steps++ < 6) {
                System.out.println("           [raw] " + com.github.library.dodp.util.XmlUtil.serialize(q));

                String listId = contentListId(q);
                if (listId != null) {
                    reportContentListCount(client, listId, 3);
                    System.out.println("[DODP] 図書 文字入力検索を終了（検索結果一覧に到達）");
                    return;
                }

                Element input = firstDescendantByLocalName(q, "inputQuestion");
                if (input != null) {
                    String inputId = attrOrChildText(input, "id");
                    System.out.println("           入力質問 id=" + inputId + " にキーワード送信");
                    q = client.getQuestions(
                            com.github.library.dodp.model.UserResponses.of(inputId, keyword));
                    continue;
                }

                Element mcq = firstDescendantByLocalName(q, "multipleChoiceQuestion");
                if (mcq != null) {
                    // 検索条件を細分化するサブメニュー。最初の選択肢で進める。
                    String mId = attrOrChildText(mcq, "id");
                    Element firstChoice = firstDescendantByLocalName(mcq, "choice");
                    String cId = firstChoice == null ? null : attrOrChildText(firstChoice, "id");
                    if (mId == null || cId == null) {
                        System.out.println("           サブメニューの選択肢を特定できず終了");
                        return;
                    }
                    System.out.println("           サブメニュー " + mId + " -> " + cId + " を選択");
                    q = client.getQuestions(
                            com.github.library.dodp.model.UserResponses.of(mId, cId));
                    continue;
                }

                // テキストのみ（該当なし等）
                System.out.println("           テキスト応答: " + nz(extractMenuText(q)));
                System.out.println("[DODP] 図書 文字入力検索を終了（一覧に到達せず）");
                return;
            }
            System.out.println("[DODP] 図書 文字入力検索を終了（ステップ上限）");
        } catch (Exception e) {
            System.out.println("[DODP] 図書 文字入力検索でエラー: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static String menuId(Element questions) {
        if (questions == null) {
            return null;
        }
        Element mcq = firstDescendantByLocalName(questions, "multipleChoiceQuestion");
        return mcq == null ? null : attrOrChildText(mcq, "id");
    }

    private static void reportContentListCount(DodpClient client, String listId, int depth) {
        try {
            // 件数のみ必要: firstItem=0, lastItem=0（1件だけ要求して totalItems を読む）
            ContentList list = client.getContentList(listId, 0, 0);
            int total = list == null ? -1 : list.getTotalItems();
            String label = (list == null) ? null : labelText(list.getLabel());
            System.out.printf("%s[図書一覧] id=%s, totalItems=%d, label=%s%n",
                    indent(depth), listId, total, nz(label));
        } catch (Exception e) {
            System.out.println(indent(depth) + "[図書一覧] id=" + listId
                    + " の件数取得でエラー: " + e.getMessage());
        }
    }

    /** questions が図書一覧（終端）を表す場合、その一覧IDを返す。そうでなければ null。 */
    private static String contentListId(Element questions) {
        // 選択肢/入力があれば終端ではない
        if (firstDescendantByLocalName(questions, "multipleChoiceQuestion") != null
                || firstDescendantByLocalName(questions, "inputQuestion") != null) {
            return null;
        }
        Element ref = firstDescendantByLocalName(questions, "contentListRef");
        if (ref != null) {
            String v = textOf(ref);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        String id = questions.hasAttribute("id") ? questions.getAttribute("id") : null;
        return (id != null && !id.isEmpty()) ? id : null;
    }

    private static String extractMenuText(Element element) {
        Element text = firstChildByLocalName(element, "text");
        if (text != null) {
            String v = textOf(text);
            if (v != null) return v;
        }
        Element label = firstChildByLocalName(element, "label");
        if (label != null) {
            Element labelText = firstChildByLocalName(label, "text");
            if (labelText != null) return textOf(labelText);
            return textOf(label);
        }
        return null;
    }

    // --- DOM ヘルパー（localName ベース、名前空間非依存） ---

    private static String localName(org.w3c.dom.Node node) {
        String ln = node.getLocalName();
        if (ln != null) return ln;
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static Element firstChildByLocalName(Element parent, String name) {
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && name.equals(localName(node))) {
                return (Element) node;
            }
        }
        return null;
    }

    private static java.util.List<Element> childrenByLocalName(Element parent, String name) {
        java.util.List<Element> result = new java.util.ArrayList<>();
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && name.equals(localName(node))) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static Element firstDescendantByLocalName(Element root, String name) {
        org.w3c.dom.NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
            Element element = (Element) node;
            if (name.equals(localName(element))) return element;
            Element found = firstDescendantByLocalName(element, name);
            if (found != null) return found;
        }
        return null;
    }

    /** 子孫すべての一致要素を文書順で返す（直接の子に限定しない）。 */
    private static java.util.List<Element> descendantsByLocalName(Element root, String name) {
        java.util.List<Element> result = new java.util.ArrayList<>();
        collectDescendants(root, name, result);
        return result;
    }

    private static void collectDescendants(Element root, String name, java.util.List<Element> out) {
        org.w3c.dom.NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
            Element element = (Element) node;
            if (name.equals(localName(element))) {
                out.add(element);
            }
            collectDescendants(element, name, out);
        }
    }

    private static String attrOrChildText(Element element, String name) {
        if (element.hasAttribute(name)) {
            String v = element.getAttribute(name);
            if (!v.isEmpty()) return v;
        }
        Element child = firstChildByLocalName(element, name);
        return child != null ? textOf(child) : null;
    }

    private static String textOf(Element element) {
        String content = element.getTextContent();
        if (content == null) return null;
        String trimmed = content.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String indent(int depth) {
        StringBuilder sb = new StringBuilder("           ");
        for (int i = 0; i < depth; i++) sb.append("  ");
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "(no text)" : s;
    }

    private static void dumpContentList(DodpClient client, String listId) {
        try {
            ContentList list = client.getContentList(listId, 0, -1);
            if (list == null) {
                System.out.println("[DODP] contentList(" + listId + ") = null");
                return;
            }
            System.out.printf("[DODP] contentList(%s) totalItems=%d, items=%d%n",
                    listId, list.getTotalItems(),
                    list.getItems() == null ? 0 : list.getItems().size());
            if (list.getItems() != null) {
                int shown = 0;
                for (ContentItem item : list.getItems()) {
                    if (shown++ >= 5) {
                        System.out.println("           ... (以降省略)");
                        break;
                    }
                    System.out.printf("           - id=%s, title=%s%n",
                            item.getId(), labelText(item.getLabel()));
                }
                // 先頭1件だけメタデータも読み取り（read-only）
                if (!list.getItems().isEmpty()) {
                    String firstId = list.getItems().get(0).getId();
                    try {
                        ContentMetadata meta = client.getContentMetadata(firstId);
                        String title = (meta == null || meta.getMetadata() == null)
                                ? "null" : meta.getMetadata().getTitle();
                        System.out.printf("           metadata(%s): title=%s%n", firstId, title);
                    } catch (Exception e) {
                        System.out.println("           metadata(" + firstId + ") error: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // 一覧の種類によっては未対応/空のことがあるため、致命ではなく記録のみ
            System.out.println("[DODP] contentList(" + listId + ") error: " + e.getMessage());
        }
    }

    private static void printServiceAttributes(ServiceAttributes service) {
        if (service == null) {
            System.out.println("           (null)");
            return;
        }
        System.out.println("           serviceProviderId = " + service.getServiceProviderId());
        System.out.println("           serviceProvider   = " + labelText(service.getServiceProviderLabel()));
        System.out.println("           serviceId         = " + service.getServiceId());
        System.out.println("           service           = " + labelText(service.getServiceLabel()));
        System.out.println("           supportsSearch    = " + service.isSupportsSearch());
        System.out.println("           supportsServerBack= " + service.isSupportsServerSideBack());
        System.out.print("           optionalOps       = ");
        if (service.getSupportedOptionalOperations() == null
                || service.getSupportedOptionalOperations().isEmpty()) {
            System.out.println("(none)");
        } else {
            StringBuilder sb = new StringBuilder();
            for (OptionalOperation op : service.getSupportedOptionalOperations()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(op);
            }
            System.out.println(sb);
        }
    }

    private static String labelText(Label label) {
        return label == null ? "(no label)" : label.getText();
    }

    /** サピエ想定の端末情報属性（{@code SapieDodpClient} に合わせる）。 */
    private static ReadingSystemAttributes buildReadingSystemAttributes() {
        return ReadingSystemAttributes.builder("AndroidDaisyReader", "AndroidDaisyReader", "1.0")
                .preferredUILanguage("ja")
                .addContentFormat("Daisy 2.02")
                .addContentFormat("ANSI/NISO Z39.86-2002")
                .addContentFormat("ANSI/NISO Z39.86-2005")
                .addInputType(InputType.TEXT_ALPHANUMERIC)
                .build();
    }
}
