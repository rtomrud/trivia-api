package com.example.trivia.model;

import org.springframework.data.relational.core.mapping.Table;

@Table("round_questions")
public class RoundQuestion {
    private String roundId;
    private String questionId;

    public RoundQuestion() {
    }

    public String getRoundId() {
        return roundId;
    }

    public void setRoundId(String roundId) {
        this.roundId = roundId;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }
}
