package org.androiddaisyreader.chattyconvert.converter;

import org.jsoup.nodes.Element;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 章ごとのマルチメディアオーバーレイSMILドキュメントを生成する。
 */
public class SmilGenerator {

    private static final Pattern SPAN_ID_PATTERN = Pattern.compile("s\\d+_\\d+");

    /**
     * 章のSMILドキュメントを生成する。
     *
     * @param chapter    章
     * @param audioMap   spanId をキーとする音声クリップのマップ
     * @param xhtmlHref  SMILファイルから見た章XHTMLへの相対パス（例: ../chapter_0001.xhtml）
     * @return SMIL文字列
     */
    public String generate(Chapter chapter, Map<String, AudioItem> audioMap, String xhtmlHref) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<smil xmlns=\"http://www.w3.org/ns/SMIL\" version=\"3.0\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
                .append("epub:prefix=\"z3998: http://www.daisy.org/z3998/2012/vocab/structure/\">\n");
        sb.append("<body>\n");
        sb.append("<seq id=\"seq_").append(chapter.getIndex()).append("\" ")
                .append("epub:textref=\"").append(xhtmlHref).append("\" ")
                .append("epub:type=\"bodymatter chapter\">\n");

        int parIndex = 1;
        for (Element el : chapter.getSectionElement().select("[id]")) {
            String id = el.id();
            if (!SPAN_ID_PATTERN.matcher(id).matches()) {
                continue;
            }
            AudioItem audio = audioMap.get(id);
            sb.append("<par id=\"par_").append(parIndex++).append("\">\n");
            sb.append("<text src=\"").append(xhtmlHref).append('#').append(id).append("\"/>\n");
            if (audio != null) {
                sb.append("<audio src=\"../").append(audio.getAudioFile()).append("\" ")
                        .append("clipBegin=\"").append(audio.getClipBegin()).append("\" ")
                        .append("clipEnd=\"").append(audio.getClipEnd()).append("\"/>\n");
            }
            sb.append("</par>\n");
        }

        sb.append("</seq>\n");
        sb.append("</body>\n</smil>");
        return sb.toString();
    }
}
