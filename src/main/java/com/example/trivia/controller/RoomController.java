package com.example.trivia.controller;

import com.example.trivia.dto.JoinRoomRequest;
import com.example.trivia.dto.JoinRoomResponse;
import com.example.trivia.dto.RoomCreationRequest;
import com.example.trivia.model.Player;
import com.example.trivia.model.Room;
import com.example.trivia.model.Team;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.TeamRepository;
import com.example.trivia.security.JwtKeyLocator;

import io.jsonwebtoken.Jwts;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@RestController
public class RoomController {
    private final PlayerRepository playerRepo;
    private final RoomRepository roomRepo;
    private final TeamRepository teamRepo;
    private final JwtKeyLocator jwtKeyLocator;

    public RoomController(
            PlayerRepository playerRepo,
            RoomRepository roomRepo,
            TeamRepository teamRepo,
            JwtKeyLocator jwtKeyLocator) {
        this.playerRepo = playerRepo;
        this.roomRepo = roomRepo;
        this.teamRepo = teamRepo;
        this.jwtKeyLocator = jwtKeyLocator;
    }

    @PostMapping("/rooms")
    public ResponseEntity<Room> createRoom(@RequestBody RoomCreationRequest request) {
        Room room = new Room();
        room.setCreatedAt(Instant.now());
        room.setCode(request.code());
        room = roomRepo.save(room);

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
    public ResponseEntity<Void> deleteRoom(@PathVariable Long roomId, HttpServletRequest request) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete a room");
        }

        roomRepo.deleteById(roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/players")
    public ResponseEntity<JoinRoomResponse> joinRoom(
            @PathVariable Long roomId,
            @RequestBody JoinRoomRequest request) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (!room.getCode().equals(request.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room code");
        }

        Player player = new Player();
        player.setRoomId(roomId);
        player.setUsername(request.username());
        player = playerRepo.save(player);

        // First player to join becomes the host
        if (playerRepo.findByRoomId(roomId).size() == 1) {
            room.setHostId(player.getPlayerId());
            roomRepo.save(room);
        }

        String jwt = Jwts.builder()
                .subject(player.getPlayerId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(this.jwtKeyLocator.locate(null))
                .compact();

        URI location = URI.create("/rooms/" + roomId + "/players/" + player.getPlayerId());
        return ResponseEntity.created(location).body(new JoinRoomResponse(player, jwt));
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
            HttpServletRequest request) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId) && !playerId.equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete another player");
        }

        playerRepo.deleteById(playerId);

        if (room.getHostId().equals(currentPlayerId)) {
            playerRepo.findByRoomId(roomId)
                    .stream()
                    .filter(player -> !player.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(player -> {
                        room.setHostId(player.getPlayerId());
                        roomRepo.save(room);
                    });
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/teams")
    public ResponseEntity<Team> createTeam(@PathVariable Long roomId, HttpServletRequest request) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can create a team");
        }

        Team team = new Team();
        team.setRoomId(roomId);
        team = teamRepo.save(team);

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
    public ResponseEntity<Void> deleteTeam(@PathVariable Long roomId, @PathVariable Long teamId, HttpServletRequest request) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete a team");
        }

        teamRepo.deleteById(teamId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms/{roomId}/teams/{teamId}/players")
    public ResponseEntity<List<Player>> getTeamPlayers(@PathVariable Long roomId, @PathVariable Long teamId) {
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
            HttpServletRequest request) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        teamRepo.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        Player player = playerRepo.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId) && !playerId.equals(currentPlayerId)) {
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
            HttpServletRequest request) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        teamRepo.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        Player player = playerRepo.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId) && !playerId.equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can remove another player from a team");
        }

        player.setTeamId(null);
        playerRepo.save(player);
        return ResponseEntity.noContent().build();
    }
}
