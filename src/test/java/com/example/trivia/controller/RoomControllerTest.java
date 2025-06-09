package com.example.trivia.controller;

import com.example.trivia.dto.JoinRoomRequest;
import com.example.trivia.dto.RoomCreationRequest;
import com.example.trivia.model.Player;
import com.example.trivia.model.Room;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.TeamRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {
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
        testRoom.setRoomId(1L);
        testRoom.setCode("TEST123");
        testRoom.setCreatedAt(Instant.now());
        
        testPlayer = new Player();
        testPlayer.setPlayerId(1L);
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
        assertEquals(testRoom.getRoomId(), response.getBody().getRoomId());
        verify(roomRepo, times(1)).save(any(Room.class));
        verify(roomRepo, times(1)).save(argThat(room -> "TEST123".equals(room.getCode())));
    }
    
    @Test
    void getRoom_returnsRoomWhenFound() {
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        ResponseEntity<Room> response = controller.getRoom(1L);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testRoom, response.getBody());
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void getRoom_throws404WhenRoomNotFound() {
        when(roomRepo.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.getRoom(1L);
        });
        
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void joinRoom_createsNewPlayerAndReturns201() {
        JoinRoomRequest request = new JoinRoomRequest("TEST123", "testUser");
        HttpSession session = mock(HttpSession.class);
        
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        when(playerRepo.save(any(Player.class))).thenReturn(testPlayer);
        
        ResponseEntity<Player> response = controller.joinRoom(1L, request, session);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testPlayer.getPlayerId(), response.getBody().getPlayerId());
        verify(roomRepo, times(1)).findById(1L);
        verify(playerRepo, times(1)).save(any(Player.class));
        verify(session, times(1)).setAttribute("1", testPlayer.getPlayerId());
    }
    
    @Test
    void joinRoom_throws404WhenRoomNotFound() {
        JoinRoomRequest request = new JoinRoomRequest("TEST123", "testUser");
        HttpSession session = mock(HttpSession.class);
        
        when(roomRepo.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.joinRoom(1L, request, session);
        });
        
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void joinRoom_throws400WhenInvalidCode() {
        JoinRoomRequest request = new JoinRoomRequest("INVALID", "testUser");
        HttpSession session = mock(HttpSession.class);
        
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.joinRoom(1L, request, session);
        });
        
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void deleteRoom_throws401WhenNotAuthenticated() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("1")).thenReturn(null);
        
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.deleteRoom(1L, session);
        });
        
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void deleteRoom_throws403WhenNotHost() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("1")).thenReturn(2L);
        
        testRoom.setHostId(1L);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.deleteRoom(1L, session);
        });
        
        verify(roomRepo, times(1)).findById(1L);
    }
    
    @Test
    void deleteRoom_deletesRoomWhenHost() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("1")).thenReturn(1L);
        
        testRoom.setHostId(1L);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        ResponseEntity<Void> response = controller.deleteRoom(1L, session);
        
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(roomRepo, times(1)).findById(1L);
        verify(roomRepo, times(1)).deleteById(1L);
    }
}
