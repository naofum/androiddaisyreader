package org.androiddaisyreader.model;

import junit.framework.TestCase;

import org.androiddaisyreader.testutilities.SampleContentDaisy31;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;

public class Opf31SpecificationTest extends TestCase {
	private static final String PATH_EBOOK_31 = "./sdcard/files-used-for-testing/testfiles/miniepub31";
	private static final String OPF_NAME = "package.opf";

	protected void setUp() throws Exception {
		super.setUp();
	}

	@SuppressWarnings("deprecation")
	public void testUsingValidSampleContentDaisy31() throws IOException {
		ByteArrayInputStream content = new ByteArrayInputStream(
				(SampleContentDaisy31.simpleValidOpf).getBytes(Charset.forName("UTF-8")));
		// This testcase must get title of daisybook, so we don't need bookcontext.
		DaisyBook daisybook = Opf31Specification.readFromStream(content, null);
		assertEquals("The Code Talkers", daisybook.getTitle());
		assertEquals("Gretchen McBride", daisybook.getAuthor());
		assertEquals("Pearson Scott Foresman", daisybook.getPublisher());
		assertEquals(new Date(2008 - 1900, 5 - 1, 9), daisybook.getDate());
	}
	
	@SuppressWarnings("deprecation")
	public void testReadFromPath() throws IOException {
		BookContext bookContext = openBook();
		InputStream contents = bookContext.getResource(OPF_NAME);
		DaisyBook daisyBook = Opf31Specification.readFromStream(contents, bookContext);
//		assertEquals(2, daisyBook.sections.size());
//		assertEquals(2, daisyBook.getChildren().size());
		assertEquals(1, daisyBook.sections.size());
		assertEquals(1, daisyBook.getChildren().size());
		assertEquals("Valentin Haüy - the father of the education for the blind", daisyBook.getTitle());
		assertEquals("Beatrice Christensen Sköld", daisyBook.getAuthor());
		assertEquals("", daisyBook.getPublisher());
		assertEquals(new Date(2008 - 1900, 2 - 1, 19), daisyBook.getDate());
	}
	
	private BookContext openBook(){
		BookContext bookContext;
		File directory = new File(PATH_EBOOK_31 + File.separator + OPF_NAME);
		boolean isDirectory = directory.isDirectory();
		if (isDirectory) {
			bookContext = new FileSystemContext(PATH_EBOOK_31);
		} else {
			bookContext = new FileSystemContext(directory.getParent());
		}
		return bookContext;
	}

}
