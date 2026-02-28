package com.example.trivia.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("round_questions")
public class RoundQuestion {
    @Id
    private RoundQuestionId id;

    public RoundQuestion() {
    }

    public RoundQuestionId getId() {
        return id;
    }

    public void setId(RoundQuestionId id) {
        this.id = id;
    }
}
