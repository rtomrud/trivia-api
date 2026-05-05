package com.example.trivia.controller;

import com.example.trivia.model.Player;
import com.example.trivia.model.Room;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.service.SseService;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {
    @Mock
    private PlayerRepository playerRepo;

    @Mock
    private RoomRepository roomRepo;

    @Mock
    private SseService sseService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private RoomController roomController;

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
    void createRoom_createsNewRoomAndReturns201() {
        when(roomRepo.save(any(Room.class))).thenReturn(testRoom);

        ResponseEntity<Room> response = roomController.createRoom("TEST123");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testRoom.getId(), response.getBody().getId());
        verify(roomRepo).save(argThat(room -> "TEST123".equals(room.getCode())));
    }

    @Test
    void getRoom_returnsRoomWhenFound() {
        when(request.getAttribute("playerId")).thenReturn(1L);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        when(playerRepo.findById(1L)).thenReturn(Optional.of(testPlayer));

        ResponseEntity<Room> response = roomController.getRoom(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testRoom, response.getBody());
        verify(roomRepo).findById(1L);
    }

    @Test
    void getRoom_throws404WhenRoomNotFound() {
        when(roomRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomController.getRoom(1L, request));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(roomRepo).findById(1L);
    }

    @Test
    void deleteRoom_deletesRoomWhenHost() {
        when(request.getAttribute("playerId")).thenReturn(1L);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));

        ResponseEntity<Void> response = roomController.deleteRoom(1L, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(roomRepo).findById(1L);
        verify(roomRepo).deleteById(1L);
    }

    @Test
    void deleteRoom_throws401WhenNotAuthenticated() {
        when(request.getAttribute("playerId")).thenReturn(null);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomController.deleteRoom(1L, request));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(roomRepo).findById(1L);
    }

    @Test
    void deleteRoom_throws403WhenNotHost() {
        when(request.getAttribute("playerId")).thenReturn(2L);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> roomController.deleteRoom(1L, request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(roomRepo).findById(1L);
    }
}
