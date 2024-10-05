package org.androiddaisyreader.model;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class XmlSpecificationTest extends TestCase {
	private static final String PATH_EBOOK_30 = "./sdcard/files-used-for-testing/testfiles/Are_you_ready_minidaisy3";
	private static final String XML_NAME = "AreYouReadyV3.xml";

	protected void setUp() throws Exception {
		super.setUp();
	}

	@SuppressWarnings("deprecation")
	public void testReadFromPath() throws IOException {
		BookContext bookContext = openBook();
		InputStream contents = bookContext.getResource(XML_NAME);
		List<XmlModel> listModel = XmlSpecification.readFromStream(contents);
		assertEquals(33, listModel.size());
	}
	
	private BookContext openBook(){
		BookContext bookContext;
		File directory = new File(PATH_EBOOK_30 + File.separator + XML_NAME);
		boolean isDirectory = directory.isDirectory();
		if (isDirectory) {
			bookContext = new FileSystemContext(PATH_EBOOK_30);
		} else {
			bookContext = new FileSystemContext(directory.getParent());
		}
		return bookContext;
	}

}
