package com.example.trivia.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("games")
public class Game {
    @Id
    private Long gameId;
    private Long roomId;
    private Integer rounds;
    private Integer timePerRound;
    private Integer questionsPerRound;
    private Integer difficulty;
    private Instant createdAt;
    private Instant endedAt;

    public Game() {
    }

    public Long getGameId() {
        return gameId;
    }

    public void setGameId(Long gameId) {
        this.gameId = gameId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public Integer getRounds() {
        return rounds;
    }

    public void setRounds(Integer rounds) {
        this.rounds = rounds;
    }

    public Integer getTimePerRound() {
        return timePerRound;
    }

    public void setTimePerRound(Integer timePerRound) {
        this.timePerRound = timePerRound;
    }

    public Integer getQuestionsPerRound() {
        return questionsPerRound;
    }

    public void setQuestionsPerRound(Integer questionsPerRound) {
        this.questionsPerRound = questionsPerRound;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
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
}
