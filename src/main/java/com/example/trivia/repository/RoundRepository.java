package com.example.trivia.repository;

import com.example.trivia.model.Round;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface RoundRepository extends CrudRepository<Round, String> {
	List<Round> findByGameId(String gameId);
}
