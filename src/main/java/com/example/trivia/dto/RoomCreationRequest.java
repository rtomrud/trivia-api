package com.example.trivia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoomCreationRequest(@JsonProperty(required = true) String code) {
}
