package com.example.trivia.repository;

import com.example.trivia.model.Settings;
import org.springframework.data.repository.CrudRepository;

public interface SettingsRepository extends CrudRepository<Settings, String> {
}
