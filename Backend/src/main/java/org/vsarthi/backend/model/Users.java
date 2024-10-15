package org.vsarthi.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Data
@Entity
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(unique = true)
    private String email;

    private String password;

    private String oauth2Provider;
    private String oauth2Id;

    @Column(unique = true)
    private String refreshToken;

    @JsonIgnore
    @OneToMany(mappedBy = "creator", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Room> createdRooms;

    @JsonIgnore
    @OneToMany(mappedBy = "addedBy", fetch = FetchType.EAGER)
    private List<Song> addedSongs;

    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private List<Vote> votes;


    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof Users user)) {
            return false;
        }

        return user.getId().equals(this.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
