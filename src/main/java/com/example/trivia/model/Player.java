package com.example.trivia.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("players")
public class Player {
    @Id
    private String playerId;
    private String roomId;
    private String teamId; // nullable (not assigned by default)
    private String username;
    private Boolean host;

    public Player() {
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Boolean isHost() {
        return host;
    }

    public void setHost(Boolean host) {
        this.host = host;
    }
}
