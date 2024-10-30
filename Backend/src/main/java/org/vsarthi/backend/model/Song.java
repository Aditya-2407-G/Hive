package org.vsarthi.backend.model;

import java.util.List;

import org.springframework.data.annotation.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "is_current", nullable = false, columnDefinition = "boolean default false")
    private boolean isCurrent = false;

    @Setter
    @Getter
    @Column(name = "queue_position")
    private Integer queuePosition;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    @Version
    private Long version;

    @ManyToOne
    @JoinColumn(name = "added_by_id")
    private Users addedBy;

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