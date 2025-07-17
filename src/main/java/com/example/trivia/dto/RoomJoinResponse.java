package com.example.trivia.dto;

import com.example.trivia.model.Player;

public record RoomJoinResponse(Player player, String token) {
}
