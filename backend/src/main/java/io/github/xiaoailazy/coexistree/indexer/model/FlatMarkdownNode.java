package io.github.xiaoailazy.coexistree.indexer.model;

public class FlatMarkdownNode {
    private String title;
    private Integer lineNum;
    private Integer level;
    private String text;
    private Integer textTokenCount;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getLineNum() { return lineNum; }
    public void setLineNum(Integer lineNum) { this.lineNum = lineNum; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Integer getTextTokenCount() { return textTokenCount; }
    public void setTextTokenCount(Integer textTokenCount) { this.textTokenCount = textTokenCount; }
}

