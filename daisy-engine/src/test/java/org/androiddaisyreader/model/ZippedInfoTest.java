package org.androiddaisyreader.model;

import android.os.Build;

import junit.framework.TestCase;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZippedInfoTest extends TestCase {
    private static final String PATH_EBOOK_31 = "./sdcard/files-used-for-testing/testfiles/miniepub3/valentin-hauy.epub";
    private static final String PATH_EBOOK_202 = "./sdcard/files-used-for-testing/testfiles/daisy202/pigs.zip";

    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testZippedDaisy202() {
        InputStream input = null;
        ZipEntry entry;
        try {
            input = new BufferedInputStream(new FileInputStream(PATH_EBOOK_202));
            ZippedBookInfo info = new ZippedBookInfo();
            DaisyBookInfo bookInfo = info.readFromZipStream(input);
            assertEquals("三匹の子ぶた", bookInfo.getTitle());
            assertEquals("ジョウジフ・ジェーコブス", bookInfo.getAuthor());
            assertEquals("（財）日本障害者リハビリテーション協会", bookInfo.getPublisher());
            assertEquals("2005-04-07", bookInfo.getDate());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            assertTrue(false);
        } catch (IOException e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    public void testDaisy202() {
        InputStream input = null;
        ZipEntry entry;
        try {
            input = new BufferedInputStream(new FileInputStream(PATH_EBOOK_202));
            ZipInputStream contents = new ZipInputStream(input);
            entry = contents.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
//                System.out.println(name);
                if (name.toLowerCase().endsWith("ncc.html") || name.toLowerCase().endsWith(".opf")) {
                    ZippedBookInfo info = new ZippedBookInfo();
//					UnicodeReader reader = new UnicodeReader(contents, "utf-8");
//					String encoding = reader.getEncoding();
//					DaisyBookInfo bookInfo = info.readFromStream(new BufferedInputStream(new ReaderInputStream(reader, encoding)));
                    DaisyBookInfo bookInfo = info.readFromStream(new BufferedInputStream(contents));
                    assertEquals("三匹の子ぶた", bookInfo.getTitle());
                    assertEquals("ジョウジフ・ジェーコブス", bookInfo.getAuthor());
                    assertEquals("（財）日本障害者リハビリテーション協会", bookInfo.getPublisher());
                    assertEquals("2005-04-07", bookInfo.getDate());
                    break;
                }
                entry = contents.getNextEntry();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            assertTrue(false);
        } catch (IOException e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    public void testEpub3() {
        InputStream input = null;
        ZipEntry entry;
        try {
            input = new BufferedInputStream(new FileInputStream(PATH_EBOOK_31));
            ZipInputStream contents = new ZipInputStream(input);
            InputStreamReader reader = new InputStreamReader(contents);
            entry = contents.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
//                System.out.println(name);
                if (name.toLowerCase().endsWith("ncc.html") || name.toLowerCase().endsWith(".opf")) {
                    ZippedBookInfo info = new ZippedBookInfo();
//                    DaisyBookInfo bookInfo = info.readFromStream(new BufferedInputStream(new ReaderInputStream(reader)));
                    DaisyBookInfo bookInfo = info.readFromStream(new BufferedInputStream(contents));
                    assertEquals("Valentin Haüy - the father of the education for the blind", bookInfo.getTitle());
                    assertEquals("Beatrice Christensen Sköld", bookInfo.getAuthor());
                    assertEquals("", bookInfo.getPublisher());
                    assertEquals("2008-02-19", bookInfo.getDate());
                    break;
                }
                entry = contents.getNextEntry();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            assertTrue(false);
        } catch (IOException e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

}
