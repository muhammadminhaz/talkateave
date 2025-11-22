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

    @ElementCollection
    private List<String> instructions = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "user_id") // optional: rename column
    private User user;

    @OneToMany(mappedBy = "bot", cascade = CascadeType.ALL)
    private List<BotDocument> documents = new ArrayList<>();
}

