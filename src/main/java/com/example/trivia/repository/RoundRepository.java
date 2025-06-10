package com.example.trivia.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.trivia.model.Round;

@Repository
public interface RoundRepository extends CrudRepository<Round, Long> {
    List<Round> findByGameId(Long gameId);
}
