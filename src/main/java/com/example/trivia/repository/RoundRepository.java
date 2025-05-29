package com.example.trivia.repository;

import com.example.trivia.model.Round;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoundRepository extends CrudRepository<Round, Long> {
    List<Round> findByGameId(Long gameId);
}
