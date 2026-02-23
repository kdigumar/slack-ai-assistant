package com.enterprise.slackassistant.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class RagDocument {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("content")
    private String content;

    @JsonProperty("keywords")
    private List<String> keywords;

    public RagDocument() {}

    public RagDocument(String id, String title, String content, List<String> keywords) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.keywords = keywords;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
}
