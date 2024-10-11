package org.vsarthi.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

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
}