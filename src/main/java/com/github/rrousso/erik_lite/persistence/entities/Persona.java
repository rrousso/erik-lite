package com.github.rrousso.erik_lite.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "personas")
@Getter @Setter @NoArgsConstructor
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String pronouns;

    @Column(length = 1000)
    private String description;

    @Column(length = 1000)
    private String otherDetails;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "persona", cascade = CascadeType.ALL)
    private List<Stanza> stanzas = new ArrayList<>();

    public Persona(String name, String pronouns, String description, String otherDetails) {
        this.name = name;
        this.pronouns = pronouns;
        this.description = description;
        this.otherDetails = otherDetails;
    }
}