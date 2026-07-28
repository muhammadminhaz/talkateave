package com.muhammadminhaz.talkateeve.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per question asked of a bot. The dashboard had no source of truth for
 * interaction counts, so its numbers and chart were hardcoded; this is that source.
 * Deliberately not a full transcript: storing user questions would be a privacy
 * liability, and counts are all the dashboard needs.
 */
@Entity
@Table(name = "bot_query", indexes = @Index(name = "idx_bot_query_bot_asked", columnList = "bot_id, asked_at"))
@Getter
@Setter
@NoArgsConstructor
public class BotQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bot_id", nullable = false)
    private UUID botId;

    @Column(name = "asked_at", nullable = false)
    private Instant askedAt = Instant.now();

    public BotQuery(UUID botId) {
        this.botId = botId;
    }
}
