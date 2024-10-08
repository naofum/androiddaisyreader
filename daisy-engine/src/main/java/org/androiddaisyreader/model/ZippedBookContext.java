package org.androiddaisyreader.model;

import android.os.Build;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Represents the BookContext for a zipped book.
 * 
 * Note: Currently this doesn't check the contents of the zip file contain a
 * valid book. We can consider adding checks e.g. by passing in a 'check' method
 * in the constructor. For now we'll start simple :)
 * 
 * @author Julian Harty
 * 
 */
public class ZippedBookContext implements BookContext {
    private ZipFile zipContents;

    protected ZippedBookContext() {
        // Do nothing.
    }

    public ZippedBookContext(String zipFilename) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || Build.VERSION.SDK_INT == 0) {
            try {
                zipContents = new ZipFile(zipFilename, Charset.forName("MS932"));
            } catch (ZipException e) {
                zipContents = new ZipFile(zipFilename);
            }
        } else {
            zipContents = new ZipFile(zipFilename);
        }
    }

    public void reopen(Charset charset) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || Build.VERSION.SDK_INT == 0) {
            zipContents = new ZipFile(zipContents.getName(), charset);
        }
    }

    public InputStream getResource(String uri) throws IOException {
        ZipEntry entry;

        Enumeration<? extends ZipEntry> e = zipContents.entries();
        while (e.hasMoreElements()) {
            entry = (ZipEntry) e.nextElement();
//            System.out.println("Checking: " + entry);

            // Note: we're blindly stripping off any folder prefix and
            // assuming that each filename in the zip file is unique. These
            // assumptions may bite us in the end with some books.
            // TODO 20120218 (jharty): Consider ways to make the algorithm more
            // robust.

            // 20130912: add "toLowerCase" to increase exactly when compare two
            // text.
            if (entry.getName().toLowerCase().contains(uri.toLowerCase())) {
                return new BufferedInputStream(zipContents.getInputStream(entry), ModelConsts.BUFFER_SIZE);
            }
        }
        return null;
    }

    public String getBaseUri() {
        return zipContents.getName();
    }

}
