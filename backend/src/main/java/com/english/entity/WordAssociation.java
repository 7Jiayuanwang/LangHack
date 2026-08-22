package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/**
 * 单词联想实体
 * 存储同义词、衍生词、常用搭配等联想关系
 */
@TableName("word_associations")
public class WordAssociation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String word;

    private String type;

    @TableField("associated_word")
    private String associatedWord;

    private String definition;

    @TableField("part_of_speech")
    private String partOfSpeech;

    @TableField("example_sentence")
    private String exampleSentence;

    private String source;

    @TableField("deleted")
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAssociatedWord() { return associatedWord; }
    public void setAssociatedWord(String associatedWord) { this.associatedWord = associatedWord; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    public String getPartOfSpeech() { return partOfSpeech; }
    public void setPartOfSpeech(String partOfSpeech) { this.partOfSpeech = partOfSpeech; }

    public String getExampleSentence() { return exampleSentence; }
    public void setExampleSentence(String exampleSentence) { this.exampleSentence = exampleSentence; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
