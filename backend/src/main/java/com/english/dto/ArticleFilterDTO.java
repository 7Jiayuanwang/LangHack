package com.english.dto;

import com.english.entity.ArticleType;

/** Filters for the teacher article picker. */
public class ArticleFilterDTO {
    private ArticleType articleType;
    private String bookVolume;
    private String contentSection;
    private Integer unitNum;
    private String keyword;
    private Integer page = 1;
    private Integer size = 20;

    public ArticleType getArticleType() { return articleType; }
    public void setArticleType(ArticleType articleType) { this.articleType = articleType; }
    public String getBookVolume() { return bookVolume; }
    public void setBookVolume(String bookVolume) { this.bookVolume = bookVolume; }
    public String getContentSection() { return contentSection; }
    public void setContentSection(String contentSection) { this.contentSection = contentSection; }
    public Integer getUnitNum() { return unitNum; }
    public void setUnitNum(Integer unitNum) { this.unitNum = unitNum; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
