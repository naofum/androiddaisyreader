package org.androiddaisyreader.model;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Represents the BookContext for a media book.
 * 
 * @author naofum
 * 
 */
public class SimpleBookContext implements BookContext {
    private String mediaUri = "";
    private String mediaMime = "";
    private int mediaFormat = 0;
    private String directoryName = "";
    private ContentResolver resolver = null;
    private DaisyBookInfo bookInfo;
    private ZippedBookContext zippedBookContext;

    public boolean isFileContext = false;
    public boolean isZippedContext = false;
    public boolean isMediaContext = false;

    protected SimpleBookContext() {
        // Do nothing.
    }

    public SimpleBookContext(String mediaUri) throws IOException {
        this.mediaUri = mediaUri;
        if (mediaUri.endsWith(".zip") || mediaUri.endsWith(".epub")) {
            // Daisy, EPUB
            InputStream input = new BufferedInputStream(new FileInputStream(mediaUri));
            ZippedBookInfo info = new ZippedBookInfo();
            bookInfo = info.readFromZipStream(input);
            if (mediaUri.endsWith(".epub")) {
                ((SimpleDaisyBookInfo) bookInfo).setEpub(true);
            } else {
                if (!((SimpleDaisyBookInfo) bookInfo).isDaisy202) {
                    ((SimpleDaisyBookInfo) bookInfo).setDaisy3(true);
                }
            }
            zippedBookContext = new ZippedBookContext(mediaUri);
            isZippedContext = true;
        } else {
            File file = new File(mediaUri);
            if (!file.isDirectory()) {
                throw new IllegalStateException("A valid directory is required");
            }
            this.directoryName = directoryName;
            String optFileName = mediaUri + "/ncc.html";
            file = new File(optFileName);
            if (file.exists()) {
                InputStream input = new BufferedInputStream(new FileInputStream(optFileName));
                ZippedBookInfo info = new ZippedBookInfo();
                bookInfo = info.readFromStream(input);
                ((SimpleDaisyBookInfo) bookInfo).setDaisy202(true);
            } else {
                optFileName = getOpfFileName(mediaUri);
                InputStream input = new BufferedInputStream(new FileInputStream(optFileName));
                ZippedBookInfo info = new ZippedBookInfo();
                bookInfo = info.readFromStream(input);
                ((SimpleDaisyBookInfo) bookInfo).setDaisy3(true);
            }
            isFileContext = true;
        }
    }

    public SimpleBookContext(String mediaUri, ContentResolver resolver) throws IOException {
        this.resolver = resolver;
        this.mediaUri = mediaUri;
        if (Build.VERSION.SDK_INT > 0) {
            InputStream input = new BufferedInputStream(resolver.openInputStream(Uri.parse(mediaUri)));
            ZippedBookInfo info = new ZippedBookInfo();
            bookInfo = info.readFromZipStream(input);
            if (((SimpleDaisyBookInfo) bookInfo).isDaisy202) {
                mediaFormat = 2; // DAISY_202_FORMAT
            } else {
                mediaMime = resolver.getType(Uri.parse(mediaUri));
                if (mediaMime == null) {
                    mediaFormat = 0; // UNKNOWN
                } else if (mediaMime.equals("application/zip")) {
                    mediaFormat = 3; // DAISY_30_FORMAT
                    ((SimpleDaisyBookInfo) bookInfo).setDaisy3(true);
                } else if (mediaMime.startsWith("application/epub")) {
                    mediaFormat = 31; // EPUB_FORMAT
                    ((SimpleDaisyBookInfo) bookInfo).setEpub(true);
                }
            }
        }
        isMediaContext = true;
    }

    public InputStream getResource(String uri) throws IOException {
        if (isFileContext) {
            String fullName = directoryName + File.separator + uri;
            InputStream contents = new FileInputStream(fullName);
            return new BufferedInputStream(contents);
        }
        if (isZippedContext) {
            return zippedBookContext.getResource(uri);
        }

        ZipInputStream zipContents = null;
        ZipEntry entry;

        if (Build.VERSION.SDK_INT > 0) {
            zipContents = new ZipInputStream(resolver.openInputStream(Uri.parse(mediaUri)));
        } else {
            // for unit test
            zipContents = new ZipInputStream(new FileInputStream(mediaUri));
        }
        entry = zipContents.getNextEntry();
        while (entry != null) {
            if (entry.getName().toLowerCase().contains(uri.toLowerCase())) {
                return new BufferedInputStream(zipContents);
            }
            entry = zipContents.getNextEntry();
        }
        return null;
    }

    public String getBaseUri() {
        return mediaUri;
    }

    public int getMediaFormat() {
        return mediaFormat;
    }

    public void setMediaFormat(int mediaFormat) {
        this.mediaFormat = mediaFormat;
    }

    public DaisyBookInfo getBookInfo() {
        return bookInfo;
    }

    public void setBookInfo(DaisyBookInfo bookInfo) {
        this.bookInfo = bookInfo;
    }

    /**
     * Gets the opf file name.
     *
     * @param path the folder contains file opf.
     * @return the opf file name
     */
    public String getOpfFileName(String path) {
        String fileName = null;
        File folder = new File(path);
        if (folder.isDirectory()) {
            File[] listOfFiles = folder.listFiles();
            if (listOfFiles != null) {
                for (File file : listOfFiles) {
                    if (file.isFile() && file.getName().endsWith(".opf")) {
                        fileName = file.getName();
                        break;
                    }
                }
            }
        }
        return fileName;
    }

}
