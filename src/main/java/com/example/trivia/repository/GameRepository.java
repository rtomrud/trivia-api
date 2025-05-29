package com.example.trivia.repository;

import com.example.trivia.model.Game;
import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends CrudRepository<Game, Long> {
    List<Game> findByRoomId(Long roomId);
}
