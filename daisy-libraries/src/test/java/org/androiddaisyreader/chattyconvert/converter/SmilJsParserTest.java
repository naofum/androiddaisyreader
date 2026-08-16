package org.androiddaisyreader.chattyconvert.converter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmilJsParserTest {

    private final SmilJsParser parser = new SmilJsParser();

    @Test
    void testParse_srcAndRefResolved() {
        String smil = "var cAudioItems = {\n"
                + "\ts001_00001:{src:'./sounds/sound00001.mp3',begin:0.030,end:2.820},\n"
                + "\ts001_00002:{ref:'s001_00001',begin:2.820,end:6.092},\n"
                + "\ts002_00001:{src:'./sounds/sound00002.mp3',begin:0.030,end:0.880}\n"
                + "} ;";

        Map<String, AudioItem> result = parser.parse(smil);

        assertEquals(3, result.size());

        AudioItem master = result.get("s001_00001");
        assertNotNull(master);
        assertEquals("sounds/sound00001.mp3", master.getAudioFile());
        assertEquals("0.03s", master.getClipBegin());
        assertEquals("2.82s", master.getClipEnd());

        AudioItem ref = result.get("s001_00002");
        assertNotNull(ref);
        assertEquals("sounds/sound00001.mp3", ref.getAudioFile());
        assertEquals("2.82s", ref.getClipBegin());
        assertEquals("6.092s", ref.getClipEnd());

        AudioItem second = result.get("s002_00001");
        assertNotNull(second);
        assertEquals("sounds/sound00002.mp3", second.getAudioFile());
    }

    @Test
    void testParse_unresolvableRefIsSkipped() {
        String smil = "var cAudioItems = {\n"
                + "\ts001_00002:{ref:'s999_99999',begin:2.820,end:6.092}\n"
                + "} ;";

        Map<String, AudioItem> result = parser.parse(smil);

        assertTrue(result.isEmpty());
    }

    @Test
    void testParse_integerClockValues() {
        String smil = "var cAudioItems = {\n"
                + "\ts001_00001:{src:'./sounds/sound00001.mp3',begin:0,end:3}\n"
                + "} ;";

        Map<String, AudioItem> result = parser.parse(smil);

        AudioItem item = result.get("s001_00001");
        assertNotNull(item);
        assertEquals("0s", item.getClipBegin());
        assertEquals("3s", item.getClipEnd());
    }

    @Test
    void testParse_emptyContent() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("var cAudioItems = {} ;").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }
}
