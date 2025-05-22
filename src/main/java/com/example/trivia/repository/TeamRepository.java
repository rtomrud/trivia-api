package com.example.trivia.repository;

import com.example.trivia.model.Team;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TeamRepository extends CrudRepository<Team, String> {
	List<Team> findByRoomId(String roomId);
}
