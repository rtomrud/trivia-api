package com.example.trivia.controller;

import com.example.trivia.component.JwtKeyLocator;
import com.example.trivia.dto.RoomJoinResponse;
import com.example.trivia.model.Player;
import com.example.trivia.model.Room;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.service.SseService;

import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerControllerTest {
    @Mock
    private JwtKeyLocator jwtKeyLocator;

    @Mock
    private PlayerRepository playerRepo;

    @Mock
    private RoomRepository roomRepo;

    @Mock
    private SseService sseService;

    @InjectMocks
    private PlayerController playerController;

    private Room testRoom;
    private Player testPlayer;

    @BeforeEach
    void setUp() {
        testRoom = new Room();
        testRoom.setId(1L);
        testRoom.setCode("TEST123");
        testRoom.setCreatedAt(Instant.now());
        testRoom.setHostId(1L);

        testPlayer = new Player();
        testPlayer.setId(1L);
        testPlayer.setRoomId(1L);
        testPlayer.setUsername("testUser");
    }

    @Test
    void joinRoom_createsNewPlayerAndReturns201() {
        when(jwtKeyLocator.locate(any())).thenReturn(
                Keys.hmacShaKeyFor("test-secret-key-1234567890123456".getBytes(StandardCharsets.UTF_8)));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        when(playerRepo.save(any(Player.class))).thenReturn(testPlayer);

        ResponseEntity<RoomJoinResponse> response = playerController.joinRoom(1L, "TEST123", "testUser");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().token());
        verify(roomRepo).findById(1L);
        verify(playerRepo).save(any(Player.class));
    }

    @Test
    void joinRoom_throws404WhenRoomNotFound() {
        when(roomRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> playerController.joinRoom(1L, "TEST123", "testUser"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(roomRepo).findById(1L);
    }

    @Test
    void joinRoom_throws400WhenInvalidCode() {
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> playerController.joinRoom(1L, "INVALID", "testUser"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(roomRepo).findById(1L);
    }
}
