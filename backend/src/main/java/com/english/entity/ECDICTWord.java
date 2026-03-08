package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * ECDICT词汇实体类
 * 用于持久化ECDICT词典数据到数据库
 */
@TableName("ecdict_words")
public class ECDICTWord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("word")
    private String word;

    @TableField("phonetic")
    private String phonetic;

    @TableField("definition")
    private String definition;

    @TableField("translation")
    private String translation;

    @TableField("pos")
    private String pos;

    @TableField("collins")
    private String collins;

    @TableField("oxford")
    private String oxford;

    @TableField("tag")
    private String tag;

    @TableField("bnc")
    private String bnc;

    @TableField("frq")
    private String frq;

    @TableField("exchange")
    private String exchange;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getPhonetic() {
        return phonetic;
    }

    public void setPhonetic(String phonetic) {
        this.phonetic = phonetic;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }

    public String getCollins() {
        return collins;
    }

    public void setCollins(String collins) {
        this.collins = collins;
    }

    public String getOxford() {
        return oxford;
    }

    public void setOxford(String oxford) {
        this.oxford = oxford;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getBnc() {
        return bnc;
    }

    public void setBnc(String bnc) {
        this.bnc = bnc;
    }

    public String getFrq() {
        return frq;
    }

    public void setFrq(String frq) {
        this.frq = frq;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }
}
