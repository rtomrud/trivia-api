package com.example.trivia.dto;

public record GameCreationRequest(
        Long roomId,
        int rounds,
        int timePerRound,
        int questionsPerRound,
        int difficulty
) {
}
