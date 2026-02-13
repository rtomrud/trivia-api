package com.example.trivia.controller;

import com.example.trivia.component.JwtKeyLocator;
import com.example.trivia.dto.RoomCreationRequest;
import com.example.trivia.dto.RoomJoinRequest;
import com.example.trivia.dto.RoomJoinResponse;
import com.example.trivia.model.Player;
import com.example.trivia.model.Room;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.TeamRepository;

import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {
    @Mock
    private JwtKeyLocator jwtKeyLocator;
    
    @Mock
    private PlayerRepository playerRepo;
    
    @Mock
    private RoomRepository roomRepo;
    
    @Mock
    private TeamRepository teamRepo;
    
    @InjectMocks
    private RoomController controller;
    
    private Room testRoom;
    private Player testPlayer;
    
    @BeforeEach
    void setUp() {
        testRoom = new Room();
        testRoom.setId(1L);
        testRoom.setCode("TEST123");
        testRoom.setCreatedAt(Instant.now());
        
        testPlayer = new Player();
        testPlayer.setId(1L);
        testPlayer.setUsername("testUser");
        testPlayer.setRoomId(1L);
    }
    
    @Test
    void createRoom_createsNewRoomAndReturns201() {
        RoomCreationRequest request = new RoomCreationRequest("TEST123");
        
        when(roomRepo.save(any(Room.class))).thenReturn(testRoom);
        
        ResponseEntity<Room> response = controller.createRoom(request);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testRoom.getId(), response.getBody().getId());
        verify(roomRepo, times(1)).save(any(Room.class));
        verify(roomRepo, times(1)).save(argThat(room -> "TEST123".equals(room.getCode())));
    }
    
    @Test
    void getRoom_returnsRoomWhenFound() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        ResponseEntity<Room> response = controller.getRoom(1L, request);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testRoom, response.getBody());
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void getRoom_throws404WhenRoomNotFound() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(roomRepo.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.getRoom(1L, request);
        });
        
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void joinRoom_createsNewPlayerAndReturns201() {
        RoomJoinRequest joinRequest = new RoomJoinRequest("TEST123", "testUser");
        when(jwtKeyLocator.locate(any())).thenReturn(
            Keys.hmacShaKeyFor("test-secret-key-1234567890123456".getBytes(StandardCharsets.UTF_8)));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        when(playerRepo.save(any(Player.class))).thenReturn(testPlayer);
        
        ResponseEntity<RoomJoinResponse> response = controller.joinRoom(1L, joinRequest);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testPlayer.getId(), response.getBody().player().getId());
        verify(roomRepo, times(1)).findById(1L);
        verify(playerRepo, times(1)).save(any(Player.class));
    }
    
    @Test
    void joinRoom_throws404WhenRoomNotFound() {
        RoomJoinRequest joinRequest = new RoomJoinRequest("TEST123", "testUser");
        when(roomRepo.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.joinRoom(1L, joinRequest);
        });
        
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void joinRoom_throws400WhenInvalidCode() {
        RoomJoinRequest joinRequest = new RoomJoinRequest("INVALID", "testUser");
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.joinRoom(1L, joinRequest);
        });
        
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void deleteRoom_throws401WhenNotAuthenticated() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("playerId")).thenReturn(null);
        
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.deleteRoom(1L, request);
        });
        
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void deleteRoom_throws403WhenNotHost() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("playerId")).thenReturn(2L);
        
        testRoom.setHostId(1L);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.deleteRoom(1L, request);
        });
        
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void deleteRoom_deletesRoomWhenHost() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("playerId")).thenReturn(1L);
        
        testRoom.setHostId(1L);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        ResponseEntity<Void> response = controller.deleteRoom(1L, request);
        
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(roomRepo, times(1)).findById(1L);
        verify(roomRepo, times(1)).deleteById(1L);
    }
}
