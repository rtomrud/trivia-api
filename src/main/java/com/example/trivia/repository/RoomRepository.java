package com.example.trivia.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.trivia.model.Room;

@Repository
public interface RoomRepository extends CrudRepository<Room, Long> {
}
