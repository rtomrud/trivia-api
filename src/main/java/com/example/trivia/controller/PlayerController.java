package com.example.trivia.controller;

import java.net.URI;
import java.util.Date;
import java.util.List;

import io.jsonwebtoken.Jwts;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.trivia.component.JwtKeyLocator;
import com.example.trivia.dto.RoomJoinResponse;
import com.example.trivia.model.Player;
import com.example.trivia.model.Room;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.service.SseService;

@RestController
public class PlayerController {
    private final JwtKeyLocator jwtKeyLocator;
    private final PlayerRepository playerRepo;
    private final RoomRepository roomRepo;
    private final SseService sseService;

    public PlayerController(
            JwtKeyLocator jwtKeyLocator,
            PlayerRepository playerRepo,
            RoomRepository roomRepo,
            SseService sseService) {
        this.jwtKeyLocator = jwtKeyLocator;
        this.playerRepo = playerRepo;
        this.roomRepo = roomRepo;
        this.sseService = sseService;
    }

    @PostMapping("/players")
    public ResponseEntity<RoomJoinResponse> joinRoom(
            @RequestParam Long roomId,
            @RequestParam(required = false) String code,
            @RequestParam String username) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (room.getCode() != null && !room.getCode().equals(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room code");
        }

        Player player = new Player();
        player.setRoomId(roomId);
        player.setUsername(username);
        player = playerRepo.save(player);

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

        URI location = URI.create("/players/" + player.getId());
        sseService.publish(roomId.toString(), "player-joined", player.getId());
        return ResponseEntity.created(location).body(new RoomJoinResponse(jwt));
    }

    @GetMapping("/players")
    public ResponseEntity<List<Player>> getPlayers(@RequestParam Long roomId) {
        roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        List<Player> players = playerRepo.findByRoomId(roomId);
        return ResponseEntity.ok(players);
    }

    @GetMapping("/players/{id}")
    public ResponseEntity<Player> getPlayer(@PathVariable Long id) {
        Player player = playerRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        return ResponseEntity.ok(player);
    }

    @DeleteMapping("/players/{id}")
    public ResponseEntity<Void> deletePlayer(@PathVariable Long id, HttpServletRequest request) {
        Player player = playerRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Room room = roomRepo.findById(player.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (!room.getHostId().equals(currentPlayerId) && !id.equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete another player");
        }

        if (room.getHostId().equals(currentPlayerId)) {
            Long hostId = playerRepo.findByRoomId(player.getRoomId())
                    .stream()
                    .filter(p -> !p.getId().equals(id))
                    .findFirst()
                    .map(Player::getId)
                    .orElse(null);

            room.setHostId(hostId);
            roomRepo.save(room);
        }

        playerRepo.deleteById(id);
        sseService.publish(player.getRoomId().toString(), "player-left", id);
        return ResponseEntity.noContent().build();
    }

}
