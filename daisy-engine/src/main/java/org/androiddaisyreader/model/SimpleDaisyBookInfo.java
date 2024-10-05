package org.androiddaisyreader.model;

public class SimpleDaisyBookInfo extends DaisyBookInfo {
    public boolean isDaisy202 = false;
    public boolean isDaisy3 = false;
    public boolean isEpub = false;
    public String version = "";

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
}
