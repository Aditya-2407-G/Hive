package org.vsarthi.backend.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Playlists {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String genre;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private Users creator;

    @JsonManagedReference
    @OneToMany(mappedBy = "playlist", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlaylistSongs> songs = new ArrayList<>();
}