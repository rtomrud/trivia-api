package com.example.trivia.controller;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import io.jsonwebtoken.Jwts;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.trivia.component.JwtKeyLocator;
import com.example.trivia.dto.RoomCreationRequest;
import com.example.trivia.dto.RoomJoinRequest;
import com.example.trivia.dto.RoomJoinResponse;
import com.example.trivia.model.Player;
import com.example.trivia.model.Room;
import com.example.trivia.model.Team;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.TeamRepository;
import com.example.trivia.service.SseService;

@RestController
public class RoomController {
    private final JwtKeyLocator jwtKeyLocator;
    private final PlayerRepository playerRepo;
    private final RoomRepository roomRepo;
    private final TeamRepository teamRepo;
    private final SseService sseService;

    public RoomController(
            JwtKeyLocator jwtKeyLocator,
            PlayerRepository playerRepo,
            RoomRepository roomRepo,
            TeamRepository teamRepo,
            SseService sseService) {
        this.jwtKeyLocator = jwtKeyLocator;
        this.playerRepo = playerRepo;
        this.roomRepo = roomRepo;
        this.teamRepo = teamRepo;
        this.sseService = sseService;
    }

    @PostMapping("/rooms")
    public ResponseEntity<Room> createRoom(@RequestBody RoomCreationRequest body) {
        Room room = new Room();
        room.setCreatedAt(Instant.now());
        room.setCode(body.code());
        room = roomRepo.save(room);

        URI roomUrl = URI.create("/rooms/" + room.getId());
        return ResponseEntity.created(roomUrl).body(room);
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<Room> getRoom(@PathVariable Long roomId, HttpServletRequest request) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        // Don't return the secret code if the player isn't in the room
        if (!room.getHostId().equals(currentPlayerId)) {
            room.setCode(null);
        }

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
        sseService.publish(roomId.toString(), "room-deleted", roomId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/rooms/{roomId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToRoomEvents(@PathVariable Long roomId, HttpServletRequest request) {
        roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Player player = playerRepo.findById(currentPlayerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        if (!player.getRoomId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only players inside the room can subscribe to its events");
        }

        return sseService.subscribe(roomId.toString());
    }

    @PostMapping("/rooms/{roomId}/players")
    public ResponseEntity<RoomJoinResponse> joinRoom(@PathVariable Long roomId, @RequestBody RoomJoinRequest body) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (!room.getCode().equals(body.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room code");
        }

        Player player = new Player();
        player.setRoomId(roomId);
        player.setUsername(body.username());
        player = playerRepo.save(player);

        // First player to join becomes the host
        if (playerRepo.findByRoomId(roomId).size() == 1) {
            room.setHostId(player.getId());
            roomRepo.save(room);
        }

        String jwt = Jwts.builder()
                .subject(player.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(this.jwtKeyLocator.locate(null))
                .compact();

        URI location = URI.create("/rooms/" + roomId + "/players/" + player.getId());
        sseService.publish(roomId.toString(), "player-joined", player.getId());
        return ResponseEntity.created(location).body(new RoomJoinResponse(player, jwt));
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
                    .filter(player -> !player.getId().equals(playerId))
                    .findFirst()
                    .ifPresent(player -> {
                        room.setHostId(player.getId());
                        roomRepo.save(room);
                    });
        }

        sseService.publish(roomId.toString(), "player-left", playerId);
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

        URI location = URI.create("/rooms/" + roomId + "/teams/" + team.getId());
        sseService.publish(roomId.toString(), "team-created", team.getId());
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
    public ResponseEntity<Void> deleteTeam(@PathVariable Long roomId, @PathVariable Long teamId,
            HttpServletRequest request) {
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
        sseService.publish(roomId.toString(), "team-deleted", teamId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/rooms/{roomId}/teams/{teamId}/players/{playerId}")
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the host can assign another player to a team");
        }

        player.setTeamId(teamId);
        playerRepo.save(player);
        sseService.publish(roomId.toString(), "player-assigned-to-team", playerId);
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the host can remove another player from a team");
        }

        player.setTeamId(null);
        playerRepo.save(player);
        sseService.publish(roomId.toString(), "player-removed-from-team", playerId);
        return ResponseEntity.noContent().build();
    }

}
