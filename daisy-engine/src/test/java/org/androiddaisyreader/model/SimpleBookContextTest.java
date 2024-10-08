package org.androiddaisyreader.model;

import junit.framework.TestCase;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

@SuppressWarnings("deprecation")
public class SimpleBookContextTest extends TestCase {
//    static final String FIVE_SECTIONS = "12231";
//    ByteArrayInputStream bookContents = null;

    @Override
    protected void setUp() {
    }

    public void testReadFromStream1_202() throws IOException {
        SimpleBookContext context = new SimpleBookContext("./sdcard/files-used-for-testing/testfiles/daisy202/pigs.zip");
        DaisyBookInfo info = context.getBookInfo();
        assertEquals("三匹の子ぶた", info.getTitle());
        assertTrue(((SimpleDaisyBookInfo)info).isDaisy202);
    }

    public void testReadFromStream2_202() throws IOException {
        SimpleBookContext context = new SimpleBookContext("./sdcard/files-used-for-testing/testfiles/daisy202/pigs.zip", null);
        DaisyBook pigs = NccSpecification.readFromStream(new BufferedInputStream(context.getResource("ncc.html")));
        assertEquals("三匹の子ぶた", pigs.getTitle());
        assertEquals(1, pigs.sections.size());
        assertEquals(1, pigs.getChildren().size());
        assertEquals(7, pigs.sections.get(0).getChildren().size());
    }

    public void testReadFromStream1_33() throws IOException {
        SimpleBookContext context = new SimpleBookContext("./sdcard/files-used-for-testing/testfiles/miniepub3/kusamakura.epub");
        DaisyBookInfo info = context.getBookInfo();
        assertEquals("草枕", info.getTitle());
        assertFalse(((SimpleDaisyBookInfo)info).isDaisy202);

        try {
            InputStream input = context.getResource("一.xhtml");
            assertNotNull(input);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
