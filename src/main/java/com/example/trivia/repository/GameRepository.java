package com.example.trivia.repository;

import com.example.trivia.model.Game;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends CrudRepository<Game, Long> {
    Page<Game> findAll(Pageable pageable);

    Page<Game> findByRoomId(Long roomId, Pageable pageable);
}
