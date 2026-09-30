package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("word_category_map")
public class WordCategoryMap {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String word;
    private String category;

    public WordCategoryMap() {}

    public WordCategoryMap(String word, String category) {
        this.word = word;
        this.category = category;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
