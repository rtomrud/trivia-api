package com.example.trivia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GameCreationRequest(
        @JsonProperty(required = true) Long roomId,
        @JsonProperty(required = true) Integer rounds,
        @JsonProperty(required = true) Integer timePerRound,
        @JsonProperty(required = true) Integer questionsPerRound) {
}
