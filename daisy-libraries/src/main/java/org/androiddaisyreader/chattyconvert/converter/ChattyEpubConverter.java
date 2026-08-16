package org.androiddaisyreader.chattyconvert.converter;

import org.androiddaisyreader.chattyconvert.exception.ChattyConvertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * Chatty Library形式の図書ディレクトリをEPUB3マルチメディアオーバーレイ形式のファイルへ変換する。
 * <p>
 * 使い方:
 * <pre>
 *   new ChattyEpubConverter().convert(inputDir, outputFile);
 * </pre>
 */
public class ChattyEpubConverter {

    private static final Logger logger = LoggerFactory.getLogger(ChattyEpubConverter.class);

    private final IndexHtmlParser indexHtmlParser = new IndexHtmlParser();
    private final SmilJsParser smilJsParser = new SmilJsParser();
    private final EpubAssembler epubAssembler = new EpubAssembler();

    /**
     * 指定ディレクトリ以下の図書を変換してEPUBファイルを出力する。
     *
     * @param inputDir   入力ディレクトリ（index.html / scripts/smil.js / sounds / images / css を含む）
     * @param outputFile 出力EPUBファイル
     * @throws ChattyConvertException 変換失敗時
     * @throws IOException            入出力エラー時
     */
    public void convert(File inputDir, File outputFile) throws ChattyConvertException, IOException {
        if (inputDir == null || !inputDir.isDirectory()) {
            throw new ChattyConvertException("入力ディレクトリが存在しません: " + inputDir);
        }

        File indexHtml = new File(inputDir, "index.html");
        if (!indexHtml.isFile()) {
            throw new ChattyConvertException("index.htmlが見つかりません: " + indexHtml.getAbsolutePath());
        }
        File smilJs = new File(inputDir, "scripts/smil.js");
        if (!smilJs.isFile()) {
            throw new ChattyConvertException("scripts/smil.jsが見つかりません: " + smilJs.getAbsolutePath());
        }

        logger.info("index.htmlとsmil.jsを解析します: {}", inputDir.getAbsolutePath());
        BookData data = indexHtmlParser.parse(readFile(indexHtml));
        Map<String, AudioItem> audioMap = smilJsParser.parse(readFile(smilJs));

        if (data.getChapters().isEmpty()) {
            throw new ChattyConvertException("index.htmlから章が見つかりませんでした");
        }
        logger.info("章: {}件, 音声クリップ: {}件", data.getChapters().size(), audioMap.size());

        epubAssembler.assemble(data, audioMap, inputDir, outputFile);
        logger.info("EPUBを出力しました: {}", outputFile.getAbsolutePath());
    }

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
