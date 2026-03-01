package com.example.trivia.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

@Table("rounds")
public class Round {
    @Id
    private Long id;
    private Long gameId;
    private Instant createdAt;
    private Instant endedAt;

    @JsonIgnore
    @MappedCollection(idColumn = "round_id")
    private Set<QuestionRef> questions = new HashSet<>();

    public Round() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Set<QuestionRef> getQuestions() {
        return questions;
    }

    public void setQuestions(Set<QuestionRef> questions) {
        this.questions = questions;
    }

    public void addQuestion(Question question) {
        QuestionRef questionRef = new QuestionRef();
        questionRef.setQuestionId(question.getId());
        questions.add(questionRef);
    }
}
