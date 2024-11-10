package org.vsarthi.backend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class PlaylistSongs {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlists playlist;

    @ManyToOne
    @JoinColumn(name = "added_by")
    private Users addedBy;

    @Column(nullable = false)
    private String songName;

    @Column(nullable = false)
    private String youtubeLink;

    private String thumbnailUrl;
    private Long duration;

    @Column(nullable = false)
    private Integer position;
}
