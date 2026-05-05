package com.example.trivia.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.trivia.model.Round;
import com.example.trivia.repository.GameRepository;
import com.example.trivia.repository.RoundRepository;

@RestController
public class RoundController {
    private final GameRepository gameRepo;
    private final RoundRepository roundRepo;

    public RoundController(GameRepository gameRepo, RoundRepository roundRepo) {
        this.gameRepo = gameRepo;
        this.roundRepo = roundRepo;
    }

    @GetMapping("/rounds")
    public ResponseEntity<List<Round>> getRounds(@RequestParam Long gameId) {
        gameRepo.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        List<Round> rounds = roundRepo.findByGameId(gameId);
        return ResponseEntity.ok(rounds);
    }

    @GetMapping("/rounds/{id}")
    public ResponseEntity<Round> getRound(@PathVariable Long id) {
        Round round = roundRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        return ResponseEntity.ok(round);
    }
}
