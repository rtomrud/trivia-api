package com.example.trivia.model;

import org.springframework.data.relational.core.mapping.Table;

@Table("round_questions")
public class QuestionRef {
    private Long questionId;

    public QuestionRef() {
    }

    public QuestionRef(Long questionId) {
        this.questionId = questionId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }
}
