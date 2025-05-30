package com.example.trivia.controller;

import com.example.trivia.dto.JoinRoomRequest;
import com.example.trivia.dto.RoomCreationRequest;
import com.example.trivia.model.*;
import com.example.trivia.repository.*;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
public class RoomController {
    private final PlayerRepository playerRepo;
    private final RoomRepository roomRepo;
    private final TeamRepository teamRepo;

    public RoomController(
            PlayerRepository playerRepo,
            RoomRepository roomRepo,
            TeamRepository teamRepo) {
        this.playerRepo = playerRepo;
        this.roomRepo = roomRepo;
        this.teamRepo = teamRepo;
    }

    @PostMapping("/rooms")
    public ResponseEntity<Room> createRoom(@RequestBody RoomCreationRequest request) {
        Room room = new Room();
        room.setCreatedAt(Instant.now());
        room.setCode(request.code());
        roomRepo.save(room);

        URI roomUrl = URI.create("/rooms/" + room.getRoomId());
        return ResponseEntity.created(roomUrl).body(room);
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<Room> getRoom(@PathVariable Long roomId) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Room room = roomOptional.get();
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long roomId, HttpSession session) {
        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null || !currentPlayer.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        roomRepo.deleteById(roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/players")
    public ResponseEntity<Player> joinRoom(
            @PathVariable Long roomId,
            @RequestBody JoinRoomRequest request,
            HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Room room = roomOptional.get();
        if (!room.getCode().equals(request.code())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Player player = new Player();
        player.setRoomId(roomId);
        player.setUsername(request.username());
        player.setHost(playerRepo.findByRoomId(roomId).isEmpty()); // first player to join is host
        playerRepo.save(player);

        session.setAttribute(roomId.toString(), player);

        URI location = URI.create("/rooms/" + roomId + "/players/" + player.getPlayerId());
        return ResponseEntity.created(location).body(player);
    }

    @GetMapping("/rooms/{roomId}/players")
    public ResponseEntity<List<Player>> getRoomPlayers(@PathVariable Long roomId) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Player> players = playerRepo.findByRoomId(roomId);
        return ResponseEntity.ok(players);
    }

    @DeleteMapping("/rooms/{roomId}/players/{playerId}")
    public ResponseEntity<Void> deletePlayer(
            @PathVariable Long roomId,
            @PathVariable Long playerId,
            HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null
                || !currentPlayer.isHost() && !currentPlayer.getPlayerId().equals(playerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        playerRepo.deleteById(playerId);

        if (currentPlayer.getPlayerId().equals(playerId)) {
        session.removeAttribute(roomId.toString());
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/teams")
    public ResponseEntity<Team> createTeam(@PathVariable Long roomId, HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null || !currentPlayer.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Team team = new Team();
        team.setRoomId(roomId);
        teamRepo.save(team);

        URI location = URI.create("/rooms/" + roomId + "/teams/" + team.getTeamId());
        return ResponseEntity.created(location).body(team);
    }

    @GetMapping("/rooms/{roomId}/teams")
    public ResponseEntity<List<Team>> getTeams(@PathVariable Long roomId) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Team> teams = teamRepo.findByRoomId(roomId);
        return ResponseEntity.ok(teams);
    }

    @DeleteMapping("/rooms/{roomId}/teams/{teamId}")
    public ResponseEntity<Void> deleteTeam(
            @PathVariable Long roomId,
            @PathVariable Long teamId,
            HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null || !currentPlayer.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        teamRepo.deleteById(teamId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms/{roomId}/teams/{teamId}/players")
    public ResponseEntity<List<Player>> getTeamPlayers(
            @PathVariable Long roomId,
            @PathVariable Long teamId) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        Optional<Team> teamOptional = teamRepo.findById(teamId);
        if (roomOptional.isEmpty() || teamOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Player> players = playerRepo.findByTeamId(teamId);
        return ResponseEntity.ok(players);
    }

    @PostMapping("/rooms/{roomId}/teams/{teamId}/players/{playerId}")
    public ResponseEntity<Void> assignPlayerToTeam(
            @PathVariable Long roomId,
            @PathVariable Long teamId,
            @PathVariable Long playerId,
            HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        Optional<Team> teamOptional = teamRepo.findById(teamId);
        Optional<Player> playerOptional = playerRepo.findById(playerId);
        if (roomOptional.isEmpty()
                || teamOptional.isEmpty()
                || playerOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null
                || !currentPlayer.isHost() && !currentPlayer.getPlayerId().equals(playerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Player player = playerOptional.get();
        player.setTeamId(teamId);
        playerRepo.save(player);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/rooms/{roomId}/teams/{teamId}/players/{playerId}")
    public ResponseEntity<Void> removePlayerFromTeam(
            @PathVariable Long roomId,
            @PathVariable Long teamId,
            @PathVariable Long playerId,
            HttpSession session) {
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        Optional<Team> teamOptional = teamRepo.findById(teamId);
        Optional<Player> playerOptional = playerRepo.findById(playerId);
        if (roomOptional.isEmpty()
                || teamOptional.isEmpty()
                || playerOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null
                || !currentPlayer.isHost() && !currentPlayer.getPlayerId().equals(playerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Player player = playerOptional.get();
        player.setTeamId(null);
        playerRepo.save(player);
        return ResponseEntity.noContent().build();
    }
}
