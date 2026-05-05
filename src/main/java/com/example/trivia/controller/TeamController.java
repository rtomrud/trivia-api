package com.example.trivia.controller;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.trivia.model.Player;
import com.example.trivia.model.Room;
import com.example.trivia.model.Team;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.TeamRepository;
import com.example.trivia.service.SseService;

@RestController
public class TeamController {
    private final PlayerRepository playerRepo;
    private final RoomRepository roomRepo;
    private final TeamRepository teamRepo;
    private final SseService sseService;

    public TeamController(
            PlayerRepository playerRepo,
            RoomRepository roomRepo,
            TeamRepository teamRepo,
            SseService sseService) {
        this.playerRepo = playerRepo;
        this.roomRepo = roomRepo;
        this.teamRepo = teamRepo;
        this.sseService = sseService;
    }

    @PostMapping("/teams")
    public ResponseEntity<Team> createTeam(@RequestParam Long roomId, HttpServletRequest request) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can create a team");
        }

        if (room.getGameId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot create a team during a game");
        }

        Team team = new Team();
        team.setRoomId(roomId);
        team = teamRepo.save(team);

        URI location = URI.create("/teams/" + team.getId());
        sseService.publish(roomId.toString(), "team-created", team.getId());
        return ResponseEntity.created(location).body(team);
    }

    @GetMapping("/teams")
    public ResponseEntity<List<Team>> getTeams(@RequestParam Long roomId) {
        roomRepo.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        List<Team> teams = teamRepo.findByRoomId(roomId);
        return ResponseEntity.ok(teams);
    }

    @GetMapping("/teams/{id}")
    public ResponseEntity<Team> getTeam(@PathVariable Long id) {
        Team team = teamRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        return ResponseEntity.ok(team);
    }

    @DeleteMapping("/teams/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id, HttpServletRequest request) {
        Team team = teamRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        Room room = roomRepo.findById(team.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete a team");
        }

        if (room.getGameId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete a team during a game");
        }

        teamRepo.deleteById(id);
        sseService.publish(room.getId().toString(), "team-deleted", id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/teams/{teamId}/players/{playerId}")
    public ResponseEntity<Void> assignPlayerToTeam(
            @PathVariable Long teamId,
            @PathVariable Long playerId,
            HttpServletRequest request) {
        Player player = playerRepo.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        teamRepo.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        Room room = roomRepo.findById(player.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId) && !playerId.equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the host can assign another player to a team");
        }

        if (room.getGameId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot assign a player to a team during a game");
        }

        player.setTeamId(teamId);
        playerRepo.save(player);
        sseService.publish(room.getId().toString(), "player-assigned-to-team", playerId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/teams/{teamId}/players/{playerId}")
    public ResponseEntity<Void> removePlayerFromTeam(
            @PathVariable Long teamId,
            @PathVariable Long playerId,
            HttpServletRequest request) {
        Player player = playerRepo.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

        teamRepo.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        Room room = roomRepo.findById(player.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId) && !playerId.equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the host can remove another player from a team");
        }

        if (room.getGameId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot remove a player from a team during a game");
        }

        player.setTeamId(null);
        playerRepo.save(player);
        sseService.publish(room.getId().toString(), "player-removed-from-team", playerId);
        return ResponseEntity.noContent().build();
    }
}
