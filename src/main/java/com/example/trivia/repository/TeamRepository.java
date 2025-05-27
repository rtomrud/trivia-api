package com.example.trivia.repository;

import com.example.trivia.model.Team;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends CrudRepository<Team, String> {
    List<Team> findByRoomId(String roomId);
}
