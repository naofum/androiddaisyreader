package org.androiddaisyreader.model;

public class SimpleDaisyBookInfo extends DaisyBookInfo {
    public boolean isDaisy202 = false;
    public boolean isDaisy3 = false;
    public boolean isEpub = false;
    private String version = "";
    private String opfFileName = "";
    private String zipCharset = "";
    private String language = "";

    public SimpleDaisyBookInfo() {
    }

    public boolean isDaysy202() {
        return isDaisy202;
    }

    public void setDaisy202(boolean daisy202) {
        isDaisy202 = daisy202;
    }

    public boolean isDaysy3() {
        return isDaisy3;
    }

    public void setDaisy3(boolean daisy3) {
        isDaisy3 = daisy3;
    }

    public boolean isEpub() {
        return isEpub;
    }

    public void setEpub(boolean epub) {
        isEpub = epub;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getOpfFileName() {
        return opfFileName;
    }

    public void setOpfFileName(String opfFileName) {
        this.opfFileName = opfFileName;
    }

    public String getZipCharset() {
        return zipCharset;
    }

    public void setZipCharset(String zipCharset) {
        this.zipCharset = zipCharset;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
