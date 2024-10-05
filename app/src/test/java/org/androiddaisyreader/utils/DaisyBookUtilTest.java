package org.androiddaisyreader.utils;

import static org.junit.Assert.*;

import org.junit.Test;

public class DaisyBookUtilTest {
    private static final String PATH_EBOOK_31 = "./src/test/sdcard/files-used-for-testing/testfiles/daisy3folder";
    private static final String OPF_NAME = "speechgen.opf";

    @Test
    public void getOpfFileName() {
        String fileName = DaisyBookUtil.getOpfFileName(PATH_EBOOK_31);
        assertEquals(OPF_NAME, fileName);
    }
}
