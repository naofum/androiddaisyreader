package org.androiddaisyreader.util;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mp3DurationTest {

    @Test
    void readSeconds_returnsPositiveForValidMp3() throws Exception {
        var url = Mp3DurationTest.class.getResource("/sample.mp3");
        assertNotNull(url, "test mp3 resource must exist");
        File mp3 = new File(url.toURI());
        double seconds = Mp3Duration.readSeconds(mp3);
        assertTrue(seconds > 0, "duration should be positive, got " + seconds);
    }

    @Test
    void readSeconds_returnsZeroForInvalidFile() throws Exception {
        File notMp3 = File.createTempFile("notmp3", ".bin");
        notMp3.deleteOnExit();
        Files.write(notMp3.toPath(), new byte[]{0x49, 0x44, 0x33});
        assertEquals(0.0, Mp3Duration.readSeconds(notMp3), 0.0001);
    }
}
