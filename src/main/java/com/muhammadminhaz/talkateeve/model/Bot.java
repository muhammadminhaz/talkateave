package com.muhammadminhaz.talkateeve.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Bot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String description;
    private String slug;

    // Instructions stored as ElementCollection
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bot_instructions", joinColumns = @JoinColumn(name = "bot_id"))
    // A textarea lets users write full paragraphs; the default varchar(255) truncated them.
    @Column(name = "instruction", length = 2000)
    private List<String> instructions = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Cascade all operations to documents and enable orphanRemoval
    @OneToMany(mappedBy = "bot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BotDocument> documents = new ArrayList<>();
}
