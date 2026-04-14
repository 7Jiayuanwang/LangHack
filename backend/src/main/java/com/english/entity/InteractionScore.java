package com.english.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;

@TableName("v_user_text_interaction_scores")
public class InteractionScore {

    @TableField("user_id")
    private Long userId;

    @TableField("text_id")
    private Long textId;

    private BigDecimal wRead;

    private BigDecimal wComplete;

    private BigDecimal wFavorite;

    private BigDecimal wDuration;

    private BigDecimal wAccuracy;

    private BigDecimal wUnderline;

    private BigDecimal interactionScore;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTextId() {
        return textId;
    }

    public void setTextId(Long textId) {
        this.textId = textId;
    }

    public BigDecimal getWRead() {
        return wRead;
    }

    public void setWRead(BigDecimal wRead) {
        this.wRead = wRead;
    }

    public BigDecimal getWComplete() {
        return wComplete;
    }

    public void setWComplete(BigDecimal wComplete) {
        this.wComplete = wComplete;
    }

    public BigDecimal getWFavorite() {
        return wFavorite;
    }

    public void setWFavorite(BigDecimal wFavorite) {
        this.wFavorite = wFavorite;
    }

    public BigDecimal getWDuration() {
        return wDuration;
    }

    public void setWDuration(BigDecimal wDuration) {
        this.wDuration = wDuration;
    }

    public BigDecimal getWAccuracy() {
        return wAccuracy;
    }

    public void setWAccuracy(BigDecimal wAccuracy) {
        this.wAccuracy = wAccuracy;
    }

    public BigDecimal getWUnderline() {
        return wUnderline;
    }

    public void setWUnderline(BigDecimal wUnderline) {
        this.wUnderline = wUnderline;
    }

    public BigDecimal getInteractionScore() {
        return interactionScore;
    }

    public void setInteractionScore(BigDecimal interactionScore) {
        this.interactionScore = interactionScore;
    }
}
