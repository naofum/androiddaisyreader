package org.androiddaisyreader.chattyconvert.converter;

import java.util.ArrayList;
import java.util.List;

/**
 * index.htmlから抽出した図書全体のデータモデル。
 */
public class BookData {

    private String title;
    private String author;
    private String publisher;
    private String producer;
    private String narrator;
    private String publicationDate;
    private String totalTime;
    private String uuid;
    private String coverImage;

    private final List<Chapter> chapters = new ArrayList<>();
    private final List<String> cssFiles = new ArrayList<>();
    private final List<String> imageFiles = new ArrayList<>();
    private final List<String> soundFiles = new ArrayList<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public String getNarrator() {
        return narrator;
    }

    public void setNarrator(String narrator) {
        this.narrator = narrator;
    }

    public String getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(String publicationDate) {
        this.publicationDate = publicationDate;
    }

    public String getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(String totalTime) {
        this.totalTime = totalTime;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public void addChapter(Chapter chapter) {
        chapters.add(chapter);
    }

    public List<String> getCssFiles() {
        return cssFiles;
    }

    public List<String> getImageFiles() {
        return imageFiles;
    }

    public List<String> getSoundFiles() {
        return soundFiles;
    }
}
