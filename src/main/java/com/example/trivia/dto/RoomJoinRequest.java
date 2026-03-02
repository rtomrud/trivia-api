package com.example.trivia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoomJoinRequest(
        @JsonProperty(required = true) String code,
        @JsonProperty(required = true) String username) {
}
