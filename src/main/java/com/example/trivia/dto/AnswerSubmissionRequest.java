package com.example.trivia.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnswerSubmissionRequest(@JsonProperty(required = true) String answer) {
}
