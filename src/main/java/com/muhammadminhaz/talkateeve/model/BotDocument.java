package com.muhammadminhaz.talkateeve.model;

import com.pgvector.PGvector;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Builder
@Table(name = "bot_document")
@AllArgsConstructor // explicit table name
public class BotDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String filename;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "embedding", columnDefinition = "vector(768)")
    private PGvector embedding;

    @ManyToOne(fetch = FetchType.LAZY) // lazy fetch for performance
    @JoinColumn(name = "bot_id", nullable = false)
    private Bot bot;
}
