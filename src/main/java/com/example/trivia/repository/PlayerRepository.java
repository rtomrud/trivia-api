package com.example.trivia.repository;

import com.example.trivia.model.Player;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerRepository extends CrudRepository<Player, Long> {
    List<Player> findByRoomId(Long roomId);

    List<Player> findByTeamId(Long teamId);
}
