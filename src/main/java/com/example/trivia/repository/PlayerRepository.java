package com.example.trivia.repository;

import com.example.trivia.model.Player;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface PlayerRepository extends CrudRepository<Player, String> {
	List<Player> findByRoomId(String roomId);

	List<Player> findByTeamId(String teamId);
}
