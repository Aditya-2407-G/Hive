package org.vsarthi.backend.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true)
    private String shareableLink;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    private Users creator;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<Song> songs = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "room_users",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<Users> joinedUsers = new HashSet<>();

    @Transient
    private Set<Users> activeUsers = new HashSet<>();

    public boolean addUser(Users user) {
        return this.joinedUsers.add(user);
    }

    public boolean hasUserJoined(Users user) {
        return this.joinedUsers.contains(user);
    }

    public Room(String name, Users creator) {
        this.name = name;
        this.creator = creator;
        this.shareableLink = UUID.randomUUID().toString();
    }



    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof Room room)) {
            return false;
        }

        return room.getId().equals(this.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public void setCurrentSong(Song song) {
        for (Song s : songs) {
            if (s.isCurrent()) {
                s.setCurrent(false);
            }
        }
        song.setCurrent(true);
    }

    public void setCreatorJoined(boolean b) {
        if (b) {
            this.joinedUsers.add(creator);
        } else {
            this.joinedUsers.remove(creator);
        }
    }
}