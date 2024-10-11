package org.vsarthi.backend.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.vsarthi.backend.model.*;
import org.vsarthi.backend.repository.RoomRepository;
import org.vsarthi.backend.repository.SongRepository;
import org.vsarthi.backend.repository.UserRepository;
import org.vsarthi.backend.repository.VoteRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final SongRepository songRepository;
    private final VoteRepository voteRepository;
    private final UserRepository userRepository;
    private final YouTubeService youTubeService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final Map<Long, Set<UserPresence>> roomPresence = new ConcurrentHashMap<>();

    @Autowired
    public RoomService(RoomRepository roomRepository, SongRepository songRepository, VoteRepository voteRepository, UserRepository userRepository, YouTubeService youTubeService, SimpMessageSendingOperations messagingTemplate) {
        this.roomRepository = roomRepository;
        this.songRepository = songRepository;
        this.voteRepository = voteRepository;
        this.userRepository = userRepository;
        this.youTubeService = youTubeService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Room createRoom(String roomName, Users creator) {
        Room room = new Room(roomName, creator);
        room.addUser(creator);
        Room savedRoom = roomRepository.save(room);

        // Notify about room creation
        messagingTemplate.convertAndSend("/topic/rooms", savedRoom);

        return savedRoom;
    }

    @Transactional
    public void addUserToRoom(Long roomId, Users user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (room.addUser(user)) {
            roomRepository.save(room);
        }

        UserPresence userPresence = new UserPresence(user.getId(), user.getUsername(), true);
        roomPresence.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(userPresence);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/users", user.getUsername() + " joined the room");

    }

    @Transactional
    public void removeUserFromRoom(Long roomId, Users user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // Remove user from presence tracking
        roomPresence.computeIfPresent(roomId, (k, v) -> {
            v.removeIf(p -> p.getUserId().equals(user.getId()));
            return v;
        });

        // Notify other users
        UserPresence presence = new UserPresence(user.getId(), user.getUsername(), false);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/presence", presence);
    }

    public Set<UserPresence> getActiveUsers(Long roomId) {
        return roomPresence.getOrDefault(roomId, Collections.emptySet());
    }

    public void handleUserDisconnection(Users user, String username) {
        // Handle user disconnection from all rooms
        roomPresence.forEach((roomId, presences) -> {
            presences.stream()
                    .filter(p -> p.getUsername().equals(username))
                    .findFirst()
                    .ifPresent(presence -> {
                        presence.setActive(false);
                        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/presence", presence);
                        presences.remove(presence);
                    });
        });
    }

    @Transactional
    public Song addSongToRoom(Long roomId, String youtubeLink, Users addedBy) throws Exception {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // Check if the user has joined the room
        boolean userJoined = room.getJoinedUsers().stream()
                .anyMatch(joinedUser -> joinedUser.getId().equals(addedBy.getId()));

        if (!userJoined) {
            throw new RuntimeException("User has not joined the room and cannot add songs");
        }

        // Check if a song with the same YouTube link already exists in this room
        Optional<Song> existingSong = songRepository.findByYoutubeLinkAndRoomId(youtubeLink, roomId);
        if (existingSong.isPresent()) {
            throw new RuntimeException("Song with the same YouTube link already exists in this room");
        }

        String videoId = youTubeService.extractVideoId(youtubeLink);
        String title = youTubeService.getVideoTitle(videoId);

        Song song = new Song();
        song.setYoutubeLink(youtubeLink);
        song.setTitle(title);
        song.setRoom(room);
        song.setAddedBy(addedBy);
        song.setCurrent(false);

        Song savedSong = songRepository.save(song);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/songs", savedSong);


        return savedSong;

    }

    public List<Song> getSongsInRoom(Long roomId) {
        return songRepository.findByRoomIdOrderByUpvotesDesc(roomId);
    }

    @Transactional
    public Song vote(Long songId, Users user, boolean isUpvote) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));

        Room room = song.getRoom();

        boolean userJoined = room.getJoinedUsers().stream()
                .anyMatch(joinedUser -> joinedUser.getId().equals(user.getId()));

        if (!userJoined) {
            throw new RuntimeException("User has not joined the room");
        }

        // Log all joined users
        System.out.println("Joined users in the room:");
        for (Users joinedUser : room.getJoinedUsers()) {
            System.out.println("User ID: " + joinedUser.getId() + ", Username: " + joinedUser.getUsername());
        }
        System.out.println("Current User ID: " + user.getId() + ", Username: " + user.getUsername());

        Optional<Vote> existingVote = voteRepository.findByUserIdAndSongId(user.getId(), songId);

        if (existingVote.isPresent()) {
            Vote vote = existingVote.get();
            if (vote.isUpvote() != isUpvote) {
                vote.setUpvote(isUpvote);
                updateSongVotes(song, isUpvote ? 2 : -2);
            }
        } else {
            Vote vote = new Vote();
            vote.setUser(user);
            vote.setSong(song);
            vote.setUpvote(isUpvote);
            voteRepository.save(vote);
            updateSongVotes(song, isUpvote ? 1 : -1);
        }

        Song updatedSong = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found after update"));

        messagingTemplate.convertAndSend("/topic/room/" + updatedSong.getRoom().getId() + "/votes", updatedSong);

        return updatedSong;
    }

    @Transactional
    public void updateSongVotes(Song song, int change) {
        if (change > 0) {
            song.setUpvotes(song.getUpvotes() + change);
        } else {
            song.setDownvotes(song.getDownvotes() - change);
        }
        songRepository.save(song);
    }

    @Transactional
    public Room joinRoom(String shareableLink, Users user) {
        Room room = roomRepository.findByShareableLink(shareableLink)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        Users dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (room.addUser(dbUser)) {
            roomRepository.save(room);
            System.out.println("User added to room: " + dbUser.getUsername());

            // Notify about user joining
            messagingTemplate.convertAndSend("/topic/room/" + room.getId() + "/users", dbUser.getUsername() + " joined the room");
        } else {
            System.out.println("User was already in the room: " + dbUser.getUsername());
        }

        return room;
    }

    @Transactional
    public String generateShareableLink(Long roomId, Users user) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if(!room.getCreator().getId().equals(user.getId())) {
            throw new RuntimeException("You are not the creator of the room");
        }

        if(room.getShareableLink() != null && !room.getShareableLink().isEmpty()) {
            return room.getShareableLink();
        }

        String sharableLink = UUID.randomUUID() + "-" + System.currentTimeMillis();
        room.setShareableLink(sharableLink);
        roomRepository.save(room);

        return sharableLink;
    }

    @Transactional
    public Room getRoomByShareableLink(String shareableLink) {
        return roomRepository.findByShareableLink(shareableLink)
                .orElseThrow(() -> new RuntimeException("Room not found"));
    }

    @Transactional
    public List<Room> getRoomsByUser(Users user) {
        return roomRepository.findAllByJoinedUsersContaining(user);
    }

    public Room getRoomDetails(Long roomId) {

        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
    }

    @Transactional
    public void closeRoom(Long roomId, Users user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getCreator().getId().equals(user.getId())) {
            throw new RuntimeException("You are not the creator of the room");
        }

        roomRepository.delete(room);

        // Notify about room closure
        messagingTemplate.convertAndSend("/topic/rooms/closed", roomId);
    }
}
