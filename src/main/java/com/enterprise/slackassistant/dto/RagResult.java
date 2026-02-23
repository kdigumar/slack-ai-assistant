package com.enterprise.slackassistant.dto;

public class RagResult {

    private String docId;
    private String title;
    private String excerpt;
    private double score;

    public RagResult() {}

    public RagResult(String docId, String title, String excerpt, double score) {
        this.docId = docId;
        this.title = title;
        this.excerpt = excerpt;
        this.score = score;
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public static RagResult of(String docId, String title, String excerpt, double score) {
        return new RagResult(docId, title, excerpt, score);
    }
}
