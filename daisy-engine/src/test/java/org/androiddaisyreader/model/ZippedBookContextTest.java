package org.androiddaisyreader.model;

import junit.framework.TestCase;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

@SuppressWarnings("deprecation")
public class ZippedBookContextTest extends TestCase {
//    static final String FIVE_SECTIONS = "12231";
//    ByteArrayInputStream bookContents = null;

    @Override
    protected void setUp() {
    }

    public void testReadFromStream1_202() throws IOException {
        ZippedBookContext context = new ZippedBookContext("./sdcard/files-used-for-testing/testfiles/daisy202/pigs.zip");
        DaisyBook pigs = NccSpecification.readFromStream(new BufferedInputStream(context.getResource("ncc.html")));
        assertEquals("三匹の子ぶた", pigs.getTitle());
        assertEquals(1, pigs.sections.size());
        assertEquals(1, pigs.getChildren().size());
        assertEquals(7, pigs.sections.get(0).getChildren().size());
    }

    public void testReadFromStream2_202() throws IOException {
        ZippedBookContext context = new ZippedBookContext("./sdcard/files-used-for-testing/testfiles/daisy202/mountains_skip.zip");
        DaisyBook book = NccSpecification.readFromStream(new BufferedInputStream(context.getResource("ncc.html")));
        assertEquals("Climbing the Highest Mountain", book.getTitle());
        assertEquals(3, book.sections.size());
        assertEquals(3, book.getChildren().size());
        assertEquals(0, book.sections.get(0).getChildren().size());
        assertEquals(3, book.sections.get(1).getChildren().size());
        assertEquals(0, book.sections.get(1).getChildren().get(0).getChildren().size());
        assertEquals(0, book.sections.get(1).getChildren().get(1).getChildren().size());
        assertEquals(2, book.sections.get(1).getChildren().get(2).getChildren().size());
        assertEquals(0, book.sections.get(0).getChildren().size());
    }

    public void testReadFromStream3_202() throws IOException {
        ZippedBookContext context = new ZippedBookContext("./sdcard/files-used-for-testing/testfiles/daisy202/1Brochure-DAISY-Consortium.zip");
        DaisyBook book = NccSpecification.readFromStream(new BufferedInputStream(context.getResource("ncc.html")));
        assertEquals("DAISY: A GLOBAL EFFORT TO PROVIDE ACCESSIBLE INFORMATION", book.getTitle());
        assertEquals(5, book.sections.size());
        assertEquals(5, book.getChildren().size());
    }

    //ToDo: 階層構造にならない
    //ToDo: 0001.smilが欠落、本来セクションは34
    public void testReadFromStream1_daisy3() throws IOException {
        ZippedBookContext context = new ZippedBookContext("./sdcard/files-used-for-testing/testfiles/daisy3/are-you-ready-z3986.zip");
        InputStream contents = context.getResource("speechgen.opf");
        DaisyBook book = OpfSpecification.readFromStream(new BufferedInputStream(contents), context);
        assertEquals("ARE YOU READY?", book.getTitle());
        assertEquals(33, book.sections.size());
        assertEquals(33, book.getChildren().size());
    }

    public void testReadFromStream1_epub3() throws IOException {
        ZippedBookContext context = new ZippedBookContext("./sdcard/files-used-for-testing/testfiles/miniepub3/valentin-hauy.epub");
        InputStream contents = context.getResource("package.opf");
        DaisyBook book = Opf31Specification.readFromStream(new BufferedInputStream(contents), context);
        assertEquals("Valentin Haüy - the father of the education for the blind", book.getTitle());
        assertEquals(1, book.sections.size());
        assertEquals(1, book.getChildren().size());
    }

    public void testReadFromStream2_epub3() throws IOException {
        ZippedBookContext context = new ZippedBookContext("./sdcard/files-used-for-testing/testfiles/miniepub3/kusamakura.epub");
        InputStream contents = context.getResource("package.opf");
        DaisyBook book = Opf31Specification.readFromStream(new BufferedInputStream(contents), context);
        assertEquals("草枕", book.getTitle());
        assertEquals(1, book.sections.size());
        assertEquals(1, book.getChildren().size());
    }
}
