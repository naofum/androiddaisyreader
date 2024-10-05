package org.androiddaisyreader.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.androiddaisyreader.apps.PrivateException;
import org.androiddaisyreader.model.BookContext;
import org.androiddaisyreader.model.DaisyBook;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.model.FileSystemContext;
import org.androiddaisyreader.model.NccSpecification;
import org.androiddaisyreader.model.Opf31Specification;
import org.androiddaisyreader.model.OpfSpecification;
import org.androiddaisyreader.model.SimpleBookContext;
import org.androiddaisyreader.model.ZippedBookContext;
import org.androiddaisyreader.model.ZippedBookInfo;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

/**
 * @author LogiGear
 * @date Jul 15, 2013
 */

public class DaisyBookUtil {
    /**
     * Search book with text.
     *
     * @param textSearch       the text search
     * @param listBook         the list recent books
     * @param listBookOriginal the list recent book original
     */
    public static List<DaisyBookInfo> searchBookWithText(CharSequence textSearch,
                                                         List<DaisyBookInfo> listBook, List<DaisyBookInfo> listBookOriginal) {
        listBook.clear();
        for (int i = 0; i < listBookOriginal.size(); i++) {
            if (listBookOriginal.get(i).getTitle().toString().toUpperCase(Locale.getDefault())
                    .contains(textSearch.toString().toUpperCase(Locale.getDefault()))) {
                listBook.add(listBookOriginal.get(i));
            }
        }
        return listBook;
    }

    /**
     * Get status of connection.
     *
     * @param context
     * @return status of connection
     */
    public static int getConnectivityStatus(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (null != activeNetwork) {
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                return Constants.CONNECT_TYPE_WIFI;
            }

            if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                return Constants.CONNECT_TYPE_MOBILE;
            }
        }
        return Constants.CONNECT_TYPE_NOT_CONNECTED;
    }

    /**
     * Tests if the directory contains the essential root file for a Daisy book
     * Currently it's limited to checking for Daisy 2.02 books.
     *
     * @param folder for the directory to check
     * @return true if the directory is deemed to contain a Daisy Book, else
     * false.
     */

    public static boolean folderContainsDaisy202Book(File folder) {
        boolean result = false;
        if (folder.getAbsolutePath().endsWith(Constants.SUFFIX_ZIP_FILE)) {
            result = zipFileContainsDaisy202Book(folder.getAbsolutePath());
        } else {

            if (!folder.isDirectory()) {
                result = false;
            }

            if (folder.getAbsolutePath().contains(Constants.FILE_NCC_NAME_NOT_CAPS)) {
                result = true;
            }

            if (new File(folder, Constants.FILE_NCC_NAME_NOT_CAPS).exists()) {
                result = true;
            }
            // Minor hack to cope with the potential of ALL CAPS filename, as
            // per
            // http://www.daisy.org/z3986/specifications/daisy_202.html#ncc
            if (new File(folder, Constants.FILE_NCC_NAME_CAPS).exists()) {
                result = true;
            }
        }

        return result;
    }

    /**
     * Does the folder represent a DAISY 3.0 book?
     *
     * @param folder the folder
     * @return true, if successful
     */
    public static boolean folderContainsDaisy30Book(File folder) {
        boolean result = false;
        if (!folder.isDirectory()) {
            result = false;
        }
        String fileName = getOpfFileName(folder.getAbsolutePath());
        if (folder.getAbsolutePath().endsWith(Constants.SUFFIX_ZIP_FILE) || folder.getAbsolutePath().endsWith(Constants.SUFFIX_EPUB_FILE)) {
            fileName = getOpfFileNameInZipFolder(folder.getAbsolutePath());
        }
        if (fileName != null) {
            result = true;
        }
        return result;
    }

    /**
     * @param filename
     * @return true if the uri has a zip file daisy book, else false.
     */
    private static boolean zipFileContainsDaisy202Book(String filename) {
        ZipEntry entry;
        ZipFile zipContents = null;
        boolean found = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // N for Nougat
                zipContents = new ZipFile(filename, Charset.forName("ISO-8859-1"));
            } else {
                zipContents = new ZipFile(filename);
            }
            Enumeration<? extends ZipEntry> e = zipContents.entries();
            while (e.hasMoreElements()) {
                entry = (ZipEntry) e.nextElement();
                if (entry.getName().contains(Constants.FILE_NCC_NAME_NOT_CAPS)
                        || entry.getName().contains(Constants.FILE_NCC_NAME_CAPS)) {
                    found = true;
                    break;
                }
            }
//            zipContents.close();
        } catch (IOException e) {
            Log.d("IOException", e.getMessage());
        } finally {
            try {
                if (zipContents != null) {
                    zipContents.close();
                }
            } catch (IOException e) {
                //
            }
        }
        return found;
    }

    /**
     * Gets the opf file name in zip folder.
     *
     * @param path the path
     * @return the opf file name in zip folder
     */
    public static String getOpfFileNameInZipFolder(String path, Context... context) {
        String result = "";
        ZipEntry entry;
        ZipFile zipContents = null;
        try {
            if (path.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
                ContentResolver resolver = context[0].getContentResolver();
                ZipInputStream contents = new ZipInputStream(new BufferedInputStream(resolver.openInputStream(Uri.parse(path))));
                entry = contents.getNextEntry();
                while (entry != null) {
                    String name = entry.getName();
                    if (name.toLowerCase().endsWith(".opf")) {
                        result = name;
                        break;
                    }
                    entry = contents.getNextEntry();
                }
                try {
                    contents.close();
                } catch (IOException e) {
                    //
                }
                return result;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // N for Nougat
                zipContents = new ZipFile(path, Charset.forName("ISO-8859-1"));
            } else {
                zipContents = new ZipFile(path);
            }
            Enumeration<? extends ZipEntry> e = zipContents.entries();
            while (e.hasMoreElements()) {
                entry = (ZipEntry) e.nextElement();
                if (entry.getName().endsWith(".opf")) {
                    result = entry.getName();
                    break;
                }
            }
            zipContents.close();
        } catch (IOException e) {
            Log.d("IOException", e.getMessage());
        }
        return result;
    }

    /**
     * return the NccFileName for a given book's root folder.
     *
     * @param currentDirectory
     * @return the filename as a string if it exists, else null.
     */
    public static String getNccFileName(File currentDirectory) {
        if (new File(currentDirectory, Constants.FILE_NCC_NAME_NOT_CAPS).exists()) {
            return Constants.FILE_NCC_NAME_NOT_CAPS;
        }

        if (new File(currentDirectory, Constants.FILE_NCC_NAME_CAPS).exists()) {
            return Constants.FILE_NCC_NAME_CAPS;
        }

        return null;
    }

    /**
     * get book context from filename.
     *
     * @param filename
     * @return book context
     * @throws IOException
     */
    public static BookContext openBook(String filename, Context... context) throws IOException {
        BookContext bookContext = null;
        if (filename.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
            bookContext = new SimpleBookContext(filename, context[0].getContentResolver());
//            InputStream contents = null;
//            contents = bookContext.getResource(Constants.FILE_NCC_NAME_NOT_CAPS);
//            if (contents != null) {
//                ((SimpleBookContext)bookContext).setMediaFormat(Constants.DAISY_202_FORMAT);
//                contents.close();
//            }
        } else {
            File directory = new File(filename);
            boolean isDirectory = directory.isDirectory();
            if (isDirectory) {
                bookContext = new FileSystemContext(filename);
            } else {
                // TODO 20130329 (jharty): think through why I used getParent
                // previously.
                bookContext = new FileSystemContext(directory.getParent());
            }
            directory = null;
            if (filename.endsWith(Constants.SUFFIX_ZIP_FILE) || filename.endsWith(Constants.SUFFIX_EPUB_FILE)) {
                bookContext = new ZippedBookContext(filename);
            } else {
                directory = new File(filename);
                bookContext = new FileSystemContext(directory.getParent());
                directory = null;
            }
        }
        return bookContext;
    }

    /**
     * open book from path
     *
     * @param path
     * @return Daisy202Book
     */
    public static DaisyBook getDaisy202Book(String path, Context... context) throws IOException {
        InputStream contents = null;
        DaisyBook book = null;
        try {
            BookContext bookContext = openBook(path, context);
            contents = bookContext.getResource(Constants.FILE_NCC_NAME_NOT_CAPS);
            if (contents == null) {
                return null;
            }
            book = NccSpecification.readFromStream(contents);
        } finally {
            if (contents != null) {
                contents.close();
            }
        }
        return book;
    }

    /**
     * Gets the daisy30 book.
     *
     * @param path the path
     * @return the daisy30 book
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static DaisyBook getDaisy30Book(String path, Context... context) throws IOException {
        InputStream contents = null;
        DaisyBook book = null;
        String filename = "";
        BookContext bookContext = null;
        try {
            if (path.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
                bookContext = openBook(path, context);
                contents = bookContext.getResource(getOpfFileNameInZipFolder(path, context));
            } else if (path.endsWith(Constants.SUFFIX_ZIP_FILE) || path.endsWith(Constants.SUFFIX_EPUB_FILE)) {
                bookContext = openBook(path, context);
                contents = bookContext.getResource(getOpfFileNameInZipFolder(path, context));
            } else {
                filename = path + File.separator + getOpfFileName(path);
                bookContext = openBook(filename, context);
                contents = bookContext.getResource(getOpfFileName(path));
            }
            if (contents != null) {
                if (path.endsWith(Constants.SUFFIX_EPUB_FILE)) {
                    book = Opf31Specification.readFromStream(contents, bookContext);
                } else {
                    book = OpfSpecification.readFromStream(contents, bookContext);
                }
            }
        } finally {
            if (contents != null) {
                contents.close();
            }
        }
        return book;
    }

    /**
     * Gets the opf file name.
     *
     * @param path the folder contains file opf.
     * @return the opf file name
     */
    public static String getOpfFileName(String path) {
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

    private static List<String> sResult;

    /**
     * Gets the daisy book.
     *
     * @param path   the path
     * @param isLoop the is loop
     * @return the daisy book
     */
    public static List<String> getDaisyBook(File path, boolean isLoop) {
        if (!isLoop) {
            sResult = new ArrayList<String>();
        }
        if (folderContainsDaisy202Book(path) || folderContainsDaisy30Book(path)) {
            sResult.add(path.getAbsolutePath());
        } else if (path.listFiles() != null) {
            File[] files = path.listFiles();
            for (File file : files) {
                if (folderContainsDaisy202Book(file) || folderContainsDaisy30Book(file)) {
                    sResult.add(file.getAbsolutePath());
                } else if (file.isDirectory()) {
                    getDaisyBook(file, true);
                }
            }
        }
        return sResult;
    }

    /**
     * Find daisy format.
     *
     * @param path the path
     * @return the int
     */
    public static int findDaisyFormat(String path, Context... context) {
        int result = 0;
        if (path.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
            InputStream contents = null;
            try {
                BookContext bookContext = openBook(path, context[0]);
                contents = bookContext.getResource("ncc.html");
            } catch (IOException e) {
                //
            }
            if (contents == null) {
                result = Constants.DAISY_30_FORMAT;
            } else {
                result = Constants.DAISY_202_FORMAT;
            }
        } else {
            File file = new File(path);
            if (folderContainsDaisy202Book(new File(path))) {
                result = Constants.DAISY_202_FORMAT;
            } else if (folderContainsDaisy30Book(file)) {
                result = Constants.DAISY_30_FORMAT;
            }
        }
        return result;
    }

    /**
     * Adds the recent book to sql lite.
     *
     * @param daisyBook the daisy book
     */
    public static void addRecentBookToSQLite(DaisyBookInfo daisyBook, int numberOfRecentBooks,
                                             SQLiteDaisyBookHelper sql) {
        if (numberOfRecentBooks > 0) {
            int lastestIdRecentBooks = 0;
            List<DaisyBookInfo> recentBooks = sql.getAllDaisyBook(Constants.TYPE_RECENT_BOOK);
            if (recentBooks.size() > 0) {
                lastestIdRecentBooks = recentBooks.get(0).getSort();
            }
            if (sql.isExists(daisyBook.getTitle(), Constants.TYPE_RECENT_BOOK)) {
                sql.deleteDaisyBook(sql.getDaisyBookByTitle(daisyBook.getTitle(),
                        Constants.TYPE_RECENT_BOOK).getId());
            }
            daisyBook.setSort(lastestIdRecentBooks + 1);
            sql.addDaisyBook(daisyBook, Constants.TYPE_RECENT_BOOK);
        }
    }

    public String getBookTitle(String path, Context context) throws PrivateException {
        DaisyBook daisyBook;
        String titleOfBook = null;
        try {
            if (path.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
                ContentResolver resolver = context.getContentResolver();
                ZippedBookInfo zippedBookInfo = new ZippedBookInfo();
                DaisyBookInfo bookInfo = zippedBookInfo.readFromZipStream(new BufferedInputStream(resolver.openInputStream(Uri.parse(path))));
                titleOfBook = bookInfo.getTitle();
            } else {
                if (DaisyBookUtil.findDaisyFormat(path) == Constants.DAISY_202_FORMAT) {
                    daisyBook = DaisyBookUtil.getDaisy202Book(path, context);
                    titleOfBook = daisyBook.getTitle() == null ? "" : daisyBook.getTitle();
                } else {
                    daisyBook = DaisyBookUtil.getDaisy30Book(path, context);
                    titleOfBook = daisyBook.getTitle() == null ? "" : daisyBook.getTitle();
                }
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, context, path);
            throw ex;
        }
        return titleOfBook;
    }
}
