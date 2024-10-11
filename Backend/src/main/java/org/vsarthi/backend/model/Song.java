package org.vsarthi.backend.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"youtube_link", "room_id"}))
public class Song {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "youtube_link")
    private String youtubeLink;

    private String title;
    private int upvotes;
    private int downvotes;

    private boolean isCurrent;  // This will track if the song is currently being played

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne
    @JoinColumn(name = "added_by_id")
    private Users addedBy;

    @OneToMany(mappedBy = "song", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Vote> votes;

    // Dynamically compute the score (upvotes - downvotes)
    public int getScore() {
        return this.upvotes - this.downvotes;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof Song song)) {
            return false;
        }

        return song.getId().equals(this.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
