package com.example.trivia.dto;

import com.example.trivia.model.Player;

public record JoinRoomResponse(Player player, String token) {
}
