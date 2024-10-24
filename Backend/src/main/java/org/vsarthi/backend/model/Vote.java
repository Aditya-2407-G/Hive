package org.vsarthi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "userVotes", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "song_id"}))
public class Vote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean isUpvote;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private Users user;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "song_id")
    private Song song;


    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof Vote vote)) {
            return false;
        }

        return vote.getId().equals(this.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
