package com.example.trivia.controller;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.trivia.model.Room;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.service.SseService;
import com.example.trivia.util.LinkHeaderBuilder;

@RestController
public class RoomController {
    private final PlayerRepository playerRepo;
    private final RoomRepository roomRepo;
    private final SseService sseService;

    public RoomController(
            PlayerRepository playerRepo,
            RoomRepository roomRepo,
            SseService sseService) {
        this.playerRepo = playerRepo;
        this.roomRepo = roomRepo;
        this.sseService = sseService;
    }

    @PostMapping("/rooms")
    public ResponseEntity<Room> createRoom(@RequestParam(required = false) String code) {
        Room room = new Room();
        room.setCreatedAt(Instant.now());
        room.setCode(code);
        room = roomRepo.save(room);

        URI roomUrl = URI.create("/rooms/" + room.getId());
        return ResponseEntity.created(roomUrl).body(room);
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<Room>> getRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Room> rooms = roomRepo.findByCodeIsNull(PageRequest.of(page, size));

        String url = UriComponentsBuilder.fromPath("/rooms")
                .replaceQueryParam("page", page)
                .replaceQueryParam("size", size)
                .toUriString();

        String linkHeader = LinkHeaderBuilder.buildWithPaginationLinks(
                rooms.getNumber(),
                rooms.getSize(),
                rooms.getTotalPages(),
                url);

        return ResponseEntity.ok().header("Link", linkHeader).body(rooms.toList());
    }

    @GetMapping("/rooms/{id}")
    public ResponseEntity<Room> getRoom(@PathVariable Long id, HttpServletRequest request) {
        Room room = roomRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (room.getCode() != null) {
            Long currentPlayerId = (Long) request.getAttribute("playerId");
            if (currentPlayerId == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
            }

            playerRepo.findById(currentPlayerId)
                    .filter(player -> player.getRoomId().equals(id))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Only players inside a private room can view it"));
        }

        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id, HttpServletRequest request) {
        Room room = roomRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete a room");
        }

        if (room.getGameId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete a room which has a game");
        }

        roomRepo.deleteById(id);
        sseService.publish(id.toString(), "room-deleted", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/rooms/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToRoomEvents(@PathVariable Long id) {
        roomRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        return sseService.subscribe(id.toString());
    }
}
