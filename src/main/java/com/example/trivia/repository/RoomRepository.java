package com.example.trivia.repository;

import com.example.trivia.model.Room;

import org.springframework.data.repository.CrudRepository;

public interface RoomRepository extends CrudRepository<Room, String> {
}
