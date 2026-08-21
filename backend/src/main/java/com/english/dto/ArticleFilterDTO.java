package com.english.dto;

import com.english.entity.ArticleType;

/** Filters for the teacher article picker. */
public class ArticleFilterDTO {
    private ArticleType articleType;
    private String bookVolume;
    private Integer unitNum;

    public ArticleType getArticleType() { return articleType; }
    public void setArticleType(ArticleType articleType) { this.articleType = articleType; }
    public String getBookVolume() { return bookVolume; }
    public void setBookVolume(String bookVolume) { this.bookVolume = bookVolume; }
    public Integer getUnitNum() { return unitNum; }
    public void setUnitNum(Integer unitNum) { this.unitNum = unitNum; }
}
