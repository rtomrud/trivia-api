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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        QuestionRef that = (QuestionRef) o;
        return java.util.Objects.equals(questionId, that.questionId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(questionId);
    }
}
