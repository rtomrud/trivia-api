package com.example.trivia.controller;

import com.example.trivia.dto.JoinRoomRequest;
import com.example.trivia.dto.RoomCreationRequest;
import com.example.trivia.model.Player;
import com.example.trivia.model.Room;
import com.example.trivia.model.Team;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.TeamRepository;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;

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
        Room room = roomRepo.findById(roomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long roomId, HttpSession session) {
        Long currentPlayerId = (Long) session.getAttribute(roomId.toString());
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Player currentPlayer = playerRepo.findById(currentPlayerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated"));

        if (!currentPlayer.isHost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete a room");
        }

        roomRepo.deleteById(roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/players")
    public ResponseEntity<Player> joinRoom(
            @PathVariable Long roomId,
            @RequestBody JoinRoomRequest request,
            HttpSession session) {
        Room room = roomRepo.findById(roomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (!room.getCode().equals(request.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room code");
        }

        Player player = new Player();
        player.setRoomId(roomId);
        player.setUsername(request.username());
        player.setHost(playerRepo.findByRoomId(roomId).isEmpty()); // first player to join is host
        playerRepo.save(player);

        session.setAttribute(room.getRoomId().toString(), player.getPlayerId());

        URI location = URI.create("/rooms/" + roomId + "/players/" + player.getPlayerId());
        return ResponseEntity.created(location).body(player);
    }

    @GetMapping("/rooms/{roomId}/players")
    public ResponseEntity<List<Player>> getRoomPlayers(@PathVariable Long roomId) {
        roomRepo.findById(roomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        List<Player> players = playerRepo.findByRoomId(roomId);
        return ResponseEntity.ok(players);
    }

    @DeleteMapping("/rooms/{roomId}/players/{playerId}")
    public ResponseEntity<Void> deletePlayer(
            @PathVariable Long roomId,
            @PathVariable Long playerId,
            HttpSession session) {
        Long currentPlayerId = (Long) session.getAttribute(roomId.toString());
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Player currentPlayer = playerRepo.findById(currentPlayerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated"));

        if (!currentPlayer.isHost() && !currentPlayer.getPlayerId().equals(playerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete another player");
        }

        if (currentPlayer.getPlayerId().equals(playerId)) {
            session.removeAttribute(roomId.toString());
        }

        playerRepo.deleteById(playerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/teams")
    public ResponseEntity<Team> createTeam(@PathVariable Long roomId, HttpSession session) {
        roomRepo.findById(roomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) session.getAttribute(roomId.toString());
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Player currentPlayer = playerRepo.findById(currentPlayerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated"));

        if (!currentPlayer.isHost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can create a team");
        }

        Team team = new Team();
        team.setRoomId(roomId);
        teamRepo.save(team);

        URI location = URI.create("/rooms/" + roomId + "/teams/" + team.getTeamId());
        return ResponseEntity.created(location).body(team);
    }

    @GetMapping("/rooms/{roomId}/teams")
    public ResponseEntity<List<Team>> getTeams(@PathVariable Long roomId) {
        roomRepo.findById(roomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        List<Team> teams = teamRepo.findByRoomId(roomId);
        return ResponseEntity.ok(teams);
    }

    @DeleteMapping("/rooms/{roomId}/teams/{teamId}")
    public ResponseEntity<Void> deleteTeam(
            @PathVariable Long roomId,
            @PathVariable Long teamId,
            HttpSession session) {
        roomRepo.findById(roomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) session.getAttribute(roomId.toString());
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Player currentPlayer = playerRepo.findById(currentPlayerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated"));

        if (!currentPlayer.isHost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete a team");
        }

        teamRepo.deleteById(teamId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms/{roomId}/teams/{teamId}/players")
    public ResponseEntity<List<Player>> getTeamPlayers(
            @PathVariable Long roomId,
            @PathVariable Long teamId) {
        roomRepo.findById(roomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        teamRepo.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        List<Player> players = playerRepo.findByTeamId(teamId);
        return ResponseEntity.ok(players);
    }

    @PostMapping("/rooms/{roomId}/teams/{teamId}/players/{playerId}")
    public ResponseEntity<Void> assignPlayerToTeam(
            @PathVariable Long roomId,
            @PathVariable Long teamId,
            @PathVariable Long playerId,
            HttpSession session) {
        roomRepo.findById(roomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        teamRepo.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        Player player = playerRepo.findById(playerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        Long currentUserId = (Long) session.getAttribute(roomId.toString());
        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Player currentPlayer = playerRepo.findById(currentUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated"));

        if (!currentPlayer.isHost() && !currentPlayer.getPlayerId().equals(player.getPlayerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can assign another player to a team");
        }

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
        roomRepo.findById(roomId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        teamRepo.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        Player player = playerRepo.findById(playerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        Long currentPlayerId = (Long) session.getAttribute(roomId.toString());
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Player currentPlayer = playerRepo.findById(currentPlayerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated"));

        if (!currentPlayer.isHost() && !currentPlayer.getPlayerId().equals(player.getPlayerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can remove another player from a team");
        }

        player.setTeamId(null);
        playerRepo.save(player);
        return ResponseEntity.noContent().build();
    }
}
