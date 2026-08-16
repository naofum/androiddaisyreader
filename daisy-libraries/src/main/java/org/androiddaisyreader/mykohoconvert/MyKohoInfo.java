package org.androiddaisyreader.mykohoconvert;

/**
 * マイ広報紙の自治体・広報情報を表すデータクラス。
 *
 * <p>自治体一覧ページ（https://mykoho.jp/lg）から取得した都道府県名・市区町村名・コードと、
 * 各自治体ページ／最新号記事一覧ページから収集した広報URL・音声URLを保持する。
 * 未取得のフィールドはnullになる。</p>
 */
public class MyKohoInfo {

    private final String prefecture;   // 都道府県名
    private final String municipality; // 市区町村名
    private final String lgCode;       // 地方公共団体コード
    private final String myKohoCode;   // マイ広報紙自治体コード
    private final String lgUrl;        // 自治体ページURL（https://mykoho.jp/lg/{lgCode}/{myKohoCode}）
    private final String kohoCode;     // マイ広報紙コード
    private final String kohoUrl;      // 最新号記事一覧URL（https://mykoho.jp/koho/{lgCode}/{kohoCode}）
    private final String pdfUrl;       // 広報URL（PDF版を見るリンク）
    private final String audioUrl;     // 音声URL（https://kohoplusblob.../{lgCode}/{kohoCode}.mp3）

    /**
     * 自治体一覧ページから取得した情報だけで構築する。広報関連のフィールドはnullになる。
     */
    public MyKohoInfo(String prefecture, String municipality, String lgCode,
                      String myKohoCode, String lgUrl) {
        this(prefecture, municipality, lgCode, myKohoCode, lgUrl, null, null, null, null);
    }

    private MyKohoInfo(String prefecture, String municipality, String lgCode,
                       String myKohoCode, String lgUrl, String kohoCode, String kohoUrl,
                       String pdfUrl, String audioUrl) {
        this.prefecture = prefecture;
        this.municipality = municipality;
        this.lgCode = lgCode;
        this.myKohoCode = myKohoCode;
        this.lgUrl = lgUrl;
        this.kohoCode = kohoCode;
        this.kohoUrl = kohoUrl;
        this.pdfUrl = pdfUrl;
        this.audioUrl = audioUrl;
    }

    /**
     * 最新号記事一覧ページの情報を設定した新しいMyKohoInfoを返す。
     *
     * @param kohoCode マイ広報紙コード
     * @param kohoUrl  最新号記事一覧URL
     * @return 記事一覧情報付きの新しいMyKohoInfo
     */
    public MyKohoInfo withKoho(String kohoCode, String kohoUrl) {
        return new MyKohoInfo(prefecture, municipality, lgCode, myKohoCode, lgUrl,
                kohoCode, kohoUrl, pdfUrl, audioUrl);
    }

    /**
     * 広報URL・音声URLを設定した新しいMyKohoInfoを返す。
     *
     * @param pdfUrl   広報URL（PDF版）
     * @param audioUrl 音声URL（mp3）
     * @return メディア情報付きの新しいMyKohoInfo
     */
    public MyKohoInfo withMedia(String pdfUrl, String audioUrl) {
        return new MyKohoInfo(prefecture, municipality, lgCode, myKohoCode, lgUrl,
                kohoCode, kohoUrl, pdfUrl, audioUrl);
    }

    public String getPrefecture() {
        return prefecture;
    }

    public String getMunicipality() {
        return municipality;
    }

    public String getLgCode() {
        return lgCode;
    }

    public String getMyKohoCode() {
        return myKohoCode;
    }

    public String getLgUrl() {
        return lgUrl;
    }

    public String getKohoCode() {
        return kohoCode;
    }

    public String getKohoUrl() {
        return kohoUrl;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    @Override
    public String toString() {
        return "MyKohoInfo{prefecture='" + prefecture + "', municipality='" + municipality
                + "', lgCode='" + lgCode + "', myKohoCode='" + myKohoCode + "', kohoCode='"
                + kohoCode + "', kohoUrl='" + kohoUrl + "', pdfUrl='" + pdfUrl
                + "', audioUrl='" + audioUrl + "'}";
    }
}
