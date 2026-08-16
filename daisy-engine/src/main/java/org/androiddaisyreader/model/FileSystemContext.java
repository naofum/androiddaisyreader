package org.androiddaisyreader.model;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class FileSystemContext implements BookContext {
    // TODO 20120214 (jharty): use more general
    private String directoryName;

    protected FileSystemContext() {
        // Do nothing.
    }

    public FileSystemContext(String directoryName) {
        File directory = new File(directoryName);
        if (!directory.isDirectory()) {
            throw new IllegalStateException("A valid directory is required");
        }
        this.directoryName = directoryName;
    }

    public InputStream getResource(String uri) throws FileNotFoundException {
        ZipSecurity.validateResourceUri(uri);
        String fullName = directoryName + File.separator + uri;
        File resolved;
        try {
            resolved = new File(fullName).getCanonicalFile();
            File base = new File(directoryName).getCanonicalFile();
            if (!resolved.getPath().startsWith(base.getPath())) {
                throw new SecurityException("Path traversal detected: " + uri);
            }
        } catch (java.io.IOException e) {
            throw new FileNotFoundException("Cannot resolve path: " + fullName);
        }
        InputStream contents = new FileInputStream(resolved);
        // A BufferedInputStream adds functionality to another input
        // stream-namely, the ability to buffer the input and to support the
        // mark and reset methods.
        return new BufferedInputStream(contents);
    }

    public String getBaseUri() {
        return directoryName;
    }

}
