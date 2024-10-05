package org.androiddaisyreader.model;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class Xml31SpecificationTest extends TestCase {
	private static final String PATH_EBOOK_31 = "./sdcard/files-used-for-testing/testfiles/miniepub31";
	private static final String XHTML_NAME = "valentinhauy11.html";

	protected void setUp() throws Exception {
		super.setUp();
	}

	@SuppressWarnings("deprecation")
	public void testReadFromPath() throws IOException {
		BookContext bookContext = openBook();
		InputStream contents = bookContext.getResource(XHTML_NAME);
		List<XmlModel> listModel = Xml31Specification.readFromStream(contents);
		assertEquals(9, listModel.size());
	}
	
	private BookContext openBook(){
		BookContext bookContext;
		File directory = new File(PATH_EBOOK_31 + File.separator + XHTML_NAME);
		boolean isDirectory = directory.isDirectory();
		if (isDirectory) {
			bookContext = new FileSystemContext(PATH_EBOOK_31);
		} else {
			bookContext = new FileSystemContext(directory.getParent());
		}
		return bookContext;
	}

}
