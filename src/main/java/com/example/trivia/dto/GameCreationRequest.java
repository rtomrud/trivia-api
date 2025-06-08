package com.example.trivia.dto;

public record GameCreationRequest(
        Long roomId,
        Integer rounds,
        Integer timePerRound,
        Integer questionsPerRound,
        Integer difficulty) {
}
