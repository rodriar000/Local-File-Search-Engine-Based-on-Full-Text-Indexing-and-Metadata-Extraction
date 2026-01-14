package com.allende.filesearch.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents a document to be indexed in Elasticsearch.
 */
public class Document {
    private String id;
    private String path;
    private String filename;
    private String extension;
    private long size;
    private Instant createdAt;
    private Instant modifiedAt;
    private String checksumSha256;
    private String content;
    private String language;
    private String author;
    private String title;
    private Instant lastIndexedAt;
    private List<String> tags;

    // Constructors
    public Document() {}

    public Document(String path, String filename) {
        this.path = path;
        this.filename = filename;
        this.lastIndexedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(Instant modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getLastIndexedAt() {
        return lastIndexedAt;
    }

    public void setLastIndexedAt(Instant lastIndexedAt) {
        this.lastIndexedAt = lastIndexedAt;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public String toString() {
        return "Document{" +
                "path='" + path + '\'' +
                ", filename='" + filename + '\'' +
                ", extension='" + extension + '\'' +
                ", size=" + size +
                ", author='" + author + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}
