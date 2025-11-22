package com.muhammadminhaz.talkateeve.repository;

import com.muhammadminhaz.talkateeve.model.Bot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BotRepository  extends JpaRepository<Bot, String> {
    List<Bot> findByUserId(UUID user_id);
    Optional<Bot> findById(UUID bot_id);
    Optional<Bot> findBySlug(String slug);
}
