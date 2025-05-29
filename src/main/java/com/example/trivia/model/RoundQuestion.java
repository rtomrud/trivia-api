package com.example.trivia.model;

import org.springframework.data.relational.core.mapping.Table;

@Table("round_questions")
public class RoundQuestion {
    private Long roundId;
    private Long questionId;

    public RoundQuestion() {
    }

    public Long getRoundId() {
        return roundId;
    }

    public void setRoundId(Long roundId) {
        this.roundId = roundId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }
}
