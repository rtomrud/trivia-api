package com.example.trivia.controller;

import com.example.trivia.model.*;
import com.example.trivia.repository.*;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.*;

@RestController
public class RoomController {
    private final RoomRepository roomRepo;
    private final PlayerRepository playerRepo;
    private final TeamRepository teamRepo;
    private final SettingsRepository settingsRepo;

    public RoomController(
            RoomRepository roomRepo,
            PlayerRepository playerRepo,
            TeamRepository teamRepo,
            SettingsRepository settingsRepo) {
        this.roomRepo = roomRepo;
        this.playerRepo = playerRepo;
        this.teamRepo = teamRepo;
        this.settingsRepo = settingsRepo;
    }

    @PostMapping("/rooms")
    public ResponseEntity<Room> createRoom() {
        Room room = new Room();
        room.setRoomId(UUID.randomUUID().toString());
        room.setCreatedAt(Instant.now());
        roomRepo.save(room);

        // Set default settings for the room
        Settings settings = new Settings();
        settings.setSettingsId(UUID.randomUUID().toString());
        settings.setRounds(10);
        settings.setTimePerRound(60);
        settings.setQuestionsPerRound(5);
        settings.setDifficulty("easy");
        settings.setMaxPlayersPerTeam(5);
        settingsRepo.save(settings);

        URI roomUrl = URI.create("/rooms/" + room.getRoomId());
        return ResponseEntity.created(roomUrl).body(room);
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<Room> getRoom(@PathVariable String roomId) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Room room = roomOptional.get();
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId,
            HttpSession session) {
        Player player = (Player) session.getAttribute(roomId);
        if (player == null || !player.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        roomRepo.deleteById(roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/players")
    public ResponseEntity<Player> joinRoom(@PathVariable String roomId,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player player = new Player();
        player.setPlayerId(UUID.randomUUID().toString());
        player.setRoomId(roomId);
        player.setUsername((String) body.get("username"));
        player.setHost(playerRepo.findByRoomId(roomId).isEmpty()); // first player to join is host
        playerRepo.save(player);

        session.setAttribute(roomId, player);
        URI location = URI.create("/rooms/" + roomId + "/players/" + player.getPlayerId());
        return ResponseEntity.created(location).body(player);
    }

    @GetMapping("/rooms/{roomId}/players")
    public ResponseEntity<List<Player>> getRoomPlayers(@PathVariable String roomId) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Player> players = playerRepo.findByRoomId(roomId);
        return ResponseEntity.ok(players);
    }

    @DeleteMapping("/rooms/{roomId}/players/{playerId}")
    public ResponseEntity<Void> deletePlayer(@PathVariable String roomId,
            @PathVariable String playerId,
            HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player player = (Player) session.getAttribute(roomId);
        if (player == null || !player.isHost() && !player.getPlayerId().equals(playerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        playerRepo.deleteById(playerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/teams")
    public ResponseEntity<Team> createTeam(@PathVariable String roomId, HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player player = (Player) session.getAttribute(roomId);
        if (player == null || !player.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Team team = new Team();
        team.setTeamId(UUID.randomUUID().toString());
        team.setRoomId(roomId);
        teamRepo.save(team);

        URI location = URI.create("/rooms/" + roomId + "/teams/" + team.getTeamId());
        return ResponseEntity.created(location).body(team);
    }

    @GetMapping("/rooms/{roomId}/teams")
    public ResponseEntity<List<Team>> getTeams(@PathVariable String roomId) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Team> teams = teamRepo.findByRoomId(roomId);
        return ResponseEntity.ok(teams);
    }

    @DeleteMapping("/rooms/{roomId}/teams/{teamId}")
    public ResponseEntity<Void> deleteTeam(@PathVariable String roomId,
            @PathVariable String teamId,
            HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player player = (Player) session.getAttribute(roomId);
        if (player == null || !player.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        teamRepo.deleteById(teamId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms/{roomId}/teams/{teamId}/players")
    public ResponseEntity<List<Player>> getTeamPlayers(@PathVariable String roomId,
            @PathVariable String teamId) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        Optional<Team> teamOptional = teamRepo.findById(teamId);
        if (roomOptional.isEmpty() || teamOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Player> players = playerRepo.findByTeamId(teamId);
        return ResponseEntity.ok(players);
    }

    @PutMapping("/rooms/{roomId}/teams/{teamId}/players/{playerId}")
    public ResponseEntity<?> assignPlayerToTeam(@PathVariable String roomId,
            @PathVariable String teamId,
            @PathVariable String playerId) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        Optional<Team> teamOptional = teamRepo.findById(teamId);
        Optional<Player> playerOptional = playerRepo.findById(playerId);
        if (roomOptional.isEmpty()
                || teamOptional.isEmpty()
                || playerOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Team assignment validation
        Room room = roomOptional.get();
        Optional<Settings> settingsOptional = settingsRepo.findById(room.getSettingsId());
        if (settingsOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<Player> players = playerRepo.findByTeamId(teamId);
        Settings settings = settingsOptional.get();
        if (players.size() >= settings.getMaxPlayersPerTeam()) {
            return ResponseEntity.badRequest().build();
        }

        Player player = playerOptional.get();
        player.setTeamId(teamId);
        playerRepo.save(player);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/rooms/{roomId}/teams/{teamId}/players/{playerId}")
    public ResponseEntity<Void> removePlayerFromTeam(@PathVariable String roomId,
            @PathVariable String teamId,
            @PathVariable String playerId,
            HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        Optional<Team> teamOptional = teamRepo.findById(teamId);
        Optional<Player> playerOptional = playerRepo.findById(playerId);
        if (roomOptional.isEmpty()
                || teamOptional.isEmpty()
                || playerOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player currentPlayer = (Player) session.getAttribute(roomId);
        if (currentPlayer == null
                || !currentPlayer.isHost() && !currentPlayer.getPlayerId().equals(playerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Player player = playerOptional.get();
        player.setTeamId(null);
        playerRepo.save(player);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms/{roomId}/settings")
    public ResponseEntity<Settings> getSettings(@PathVariable String roomId) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Room room = roomOptional.get();
        Optional<Settings> settingsOptional = settingsRepo.findById(room.getSettingsId());
        if (settingsOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Settings settings = settingsOptional.get();
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/rooms/{roomId}/settings")
    public ResponseEntity<Settings> updateSettings(@PathVariable String roomId,
            @RequestBody Settings settings,
            HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player player = (Player) session.getAttribute(roomId);
        if (player == null || !player.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        settingsRepo.save(settings);
        return ResponseEntity.ok(settings);
    }
}
