package com.example.trivia.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.trivia.model.Game;

@Repository
public interface GameRepository extends CrudRepository<Game, Long> {
    Page<Game> findAll(Pageable pageable);

    Page<Game> findByRoomId(Long roomId, Pageable pageable);

    boolean existsByRoomId(Long roomId);
}
