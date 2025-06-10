package com.example.trivia.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.trivia.model.Team;

@Repository
public interface TeamRepository extends CrudRepository<Team, Long> {
    List<Team> findByRoomId(Long roomId);
}
