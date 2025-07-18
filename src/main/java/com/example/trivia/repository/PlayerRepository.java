package com.example.trivia.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.trivia.model.Player;

@Repository
public interface PlayerRepository extends CrudRepository<Player, Long> {
    List<Player> findByRoomId(Long roomId);
}
