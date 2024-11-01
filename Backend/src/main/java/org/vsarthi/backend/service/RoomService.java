package org.vsarthi.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.vsarthi.backend.DTO.SongEndedResponse;
import org.vsarthi.backend.model.Room;
import org.vsarthi.backend.model.Song;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.repository.RoomRepository;
import org.vsarthi.backend.repository.SongRepository;
import org.vsarthi.backend.repository.UserRepository;
import org.vsarthi.backend.repository.VoteRepository;

import jakarta.transaction.Transactional;


@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final SongRepository songRepository;
    private final VoteRepository voteRepository;
    private final UserRepository userRepository;
    private final YouTubeService youTubeService;
    private final Map<Long, Set<String>> activeSessionsInRoom = new ConcurrentHashMap<>();
    private final SimpMessageSendingOperations messagingTemplate;
    private final VotingService votingService;

    @Autowired
    public RoomService(RoomRepository roomRepository, SongRepository songRepository, VoteRepository voteRepository, UserRepository userRepository, YouTubeService youTubeService, SimpMessageSendingOperations messagingTemplate, VotingService votingService) {
        this.roomRepository = roomRepository;
        this.songRepository = songRepository;
        this.voteRepository = voteRepository;
        this.userRepository = userRepository;
        this.youTubeService = youTubeService;
        this.messagingTemplate = messagingTemplate;
        this.votingService = votingService;
    }

    @Transactional
    public Room createRoom(String roomName, Users creator) {
        Room room = new Room(roomName, creator);
        room.addUser(creator);
        return roomRepository.save(room);
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

    return savedSong;

    }

    public List<Song> getSongsInRoom(Long roomId) {
        List<Song> allSongs = songRepository.findByRoomIdOrderByUpvotesDesc(roomId);
        List<Song> queuedSongs = new ArrayList<>();
        Song currentSong = null;

        // Separate current song from queue
        for (Song song : allSongs) {
            if (song.isCurrent()) {
                currentSong = song;
            } else {
                queuedSongs.add(song);
            }
        }

        // Sort queued songs by votes and queue
        queuedSongs.sort((a, b) -> {
            // First compare by votes
            int voteComparison = Integer.compare(b.getUpvotes(), a.getUpvotes());

            // If votes are equal, compare by queue position
            if (voteComparison == 0) {
                // Handle null queue positions (shouldn't happen, but just in case)
                int posA = a.getQueuePosition() != null ? a.getQueuePosition() : Integer.MAX_VALUE;
                int posB = b.getQueuePosition() != null ? b.getQueuePosition() : Integer.MAX_VALUE;
                return Integer.compare(posA, posB);
            }

            return voteComparison;
        });

        // Update queue positions
        for (int i = 0; i < queuedSongs.size(); i++) {
            queuedSongs.get(i).setQueuePosition(i + 1);
        }

        // Save updated queue positions
        songRepository.saveAll(queuedSongs);

        // Combine current song with queue
        List<Song> result = new ArrayList<>();
        if (currentSong != null) {
            currentSong.setQueuePosition(null); // Current song has no queue position
            result.add(currentSong);
        }
        result.addAll(queuedSongs);

        return result;
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
    public Song updateCurrentSong(Long roomId, Long songId, Users user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getCreator().getId().equals(user.getId())) {
            throw new RuntimeException("You are not the creator of the room, Only the creator can update the current song");
        }

        // First, unset current song if exists
        Optional<Song> currentSong = songRepository.findByRoomIdAndIsCurrent(roomId, true);
        currentSong.ifPresent(song -> {
            song.setCurrent(false);
            song.setQueuePosition(0); // Add to beginning of queue
            songRepository.save(song);
        });

        // Set new current song
        Song newCurrentSong = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));

        if (!newCurrentSong.getRoom().getId().equals(roomId)) {
            throw new RuntimeException("Song does not belong to the room");
        }

        newCurrentSong.setCurrent(true);
        newCurrentSong.setQueuePosition(null); // Remove from queue
        Song saved = songRepository.save(newCurrentSong);

        // Reorder queue
        List<Song> queuedSongs = getSongsInRoom(roomId);

        return saved;
    }

    @Transactional
    public void closeRoom(Long roomId, Users user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getCreator().getId().equals(user.getId())) {
            throw new RuntimeException("You are not the creator of the room");
        }

        try {
            // Clear the joined users set
            room.getJoinedUsers().clear();
            roomRepository.save(room);  // Save to update the join table

            // Clean up all votes for songs in this room
            List<Song> roomSongs = songRepository.findByRoomId(roomId);
            for (Song song : roomSongs) {
                voteRepository.deleteBySong(song);
            }

            // Clean up active sessions
            activeSessionsInRoom.remove(roomId);

            // Finally delete the room (this will cascade to songs due to orphanRemoval=true)
            roomRepository.delete(room);

            // Flush to ensure immediate deletion
            roomRepository.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete room: " + e.getMessage(), e);
        }
    }


    @Transactional
    public Room joinRoom(String shareableLink, Users user) {
        Room room = roomRepository.findByShareableLink(shareableLink)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        Users dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (room.addUser(dbUser)) {
            System.out.println("User added to room: " + dbUser.getUsername());
            // Remove the following line:
            // addActiveUser(room.getId(), dbUser.getUsername());
        } else {
            System.out.println("User was already in the room: " + dbUser.getUsername());
        }

        return room;
    }



    public void handleUserDisconnection(String sessionId) {
        for (Map.Entry<Long, Set<String>> entry : activeSessionsInRoom.entrySet()) {
            if (entry.getValue().remove(sessionId)) {
                Long roomId = entry.getKey();
                Integer activeUsers = entry.getValue().size();
                messagingTemplate.convertAndSend("/topic/room/" + roomId + "/activeUsers", activeUsers);
            }
        }
    }

    public Integer getActiveUsersCount(Long roomId) {
        Set<String> roomSessions = activeSessionsInRoom.get(roomId);
        return roomSessions != null ? roomSessions.size() : 0;
    }


    public Boolean isCreator(Long roomId, Users user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        return room.getCreator().getId().equals(user.getId());
    }

    @Transactional
    public boolean addActiveUser(Long roomId, String sessionId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        Set<String> roomSessions = activeSessionsInRoom.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());
        boolean wasAdded = roomSessions.add(sessionId);

        if (wasAdded) {
            System.out.println("Active user added to room " + roomId + ": " + sessionId);
        } else {
            System.out.println("User already active in room " + roomId + ": " + sessionId);
        }

        boolean isCreator = room.getCreator().getId().equals(getUserIdFromSessionId(sessionId));
        if (isCreator) {
            room.setCreatorJoined(true);
            roomRepository.save(room);
        }

        return isCreator;
    }

    private Long getUserIdFromSessionId(String sessionId) {
        return Long.parseLong(sessionId.split("-")[0]);
    }

    @Transactional
    public Integer removeActiveUser(Long roomId, String sessionId) {
        Set<String> roomSessions = activeSessionsInRoom.get(roomId);
        if (roomSessions != null) {
            roomSessions.remove(sessionId);
            if (roomSessions.isEmpty()) {
                activeSessionsInRoom.remove(roomId);
                return 0;
            }
            return roomSessions.size();
        }
        return 0;
    }

    @Transactional
    public SongEndedResponse handleSongEnded(Long roomId, Long songId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        Song endedSong = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));

        if (!endedSong.getRoom().getId().equals(roomId)) {
            throw new RuntimeException("Song does not belong to the room");
        }

        votingService.removeVotes(songId);

        // Reset ended song
        endedSong.setCurrent(false);
        endedSong.setUpvotes(0);
        endedSong.setQueuePosition(Integer.MAX_VALUE); // Place at end of queue
        voteRepository.deleteBySong(endedSong);
        songRepository.save(endedSong);


        // Find next song with highest votes
        List<Song> remainingSongs = songRepository.findByRoomIdAndIsCurrentFalseOrderByUpvotesDesc(roomId);
        Song nextSong = null;
        if (!remainingSongs.isEmpty()) {
            nextSong = remainingSongs.getFirst();
            nextSong.setCurrent(true);
            nextSong.setQueuePosition(null);
            songRepository.save(nextSong);
        }

        // Update queue positions for remaining songs
        List<Song> updatedQueue = getSongsInRoom(roomId);

        return new SongEndedResponse(
                songId,
                updatedQueue.stream().map(Song::getId).toList()
        );
    }

    @Transactional
    public Song playNow(Long roomId, Long songId, Users user) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getCreator().getId().equals(user.getId())) {
            throw new RuntimeException("Only the room creator can play songs immediately");
        }


        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));

        if (!song.getRoom().getId().equals(roomId)) {
            throw new RuntimeException("Song does not belong to the room");
        }

        if (song.isCurrent()) {
            throw new RuntimeException("Song is already playing");
        }

        // Unset current song if exists and reset its votes
        Optional<Song> currentSong = songRepository.findByRoomIdAndIsCurrent(roomId, true);
        currentSong.ifPresent(s -> {
            s.setCurrent(false);
            s.setUpvotes(0);
            voteRepository.deleteBySong(s);
            songRepository.save(s);
        });

        votingService.removeVotes(songId);

        // Set new current song
        song.setCurrent(true);
        song.setQueuePosition(null);
        song.setUpvotes(0);
        voteRepository.deleteBySong(song);
        Song savedSong = songRepository.save(song);

        // Update queue and notify clients
        List<Song> updatedSongs = getSongsInRoom(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/songs", updatedSongs);
        return savedSong;
    }

    @Transactional
    public void removeSong(Long roomId, Long songId, Users user) {
        // Validate room and user permissions
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found with ID: " + roomId));

        if (!room.getCreator().getId().equals(user.getId())) {
            throw new IllegalStateException("User " + user.getUsername() + " is not authorized to remove songs in this room");
        }

        // Find and validate the song
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new IllegalArgumentException("Song not found with ID: " + songId));

        // Validate song belongs to room
        if (!song.getRoom().getId().equals(roomId)) {
            throw new IllegalStateException("Song with ID " + songId + " does not belong to room " + roomId);
        }

        // Check if song is currently playing
        if (song.isCurrent()) {
            throw new IllegalStateException("Cannot remove currently playing song");
        }

        try {
            // First remove all votes associated with the song
            voteRepository.deleteBySong(song);

            // Remove the song from the room's song set
            room.getSongs().remove(song);
            votingService.removeVotes(songId);
            // Delete the song
            songRepository.delete(song);

            // Update queue positions for remaining songs
            List<Song> remainingSongs = getSongsInRoom(roomId);
            for (int i = 0; i < remainingSongs.size(); i++) {
                Song remainingSong = remainingSongs.get(i);
                if (!remainingSong.isCurrent()) {
                    remainingSong.setQueuePosition(i);
                    songRepository.save(remainingSong);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove song: " + e.getMessage(), e);
        }
    }

    @Transactional
    public boolean leaveRoom(Long roomId, String sessionId, String email) {
        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found"));

            Set<String> roomSessions = activeSessionsInRoom.get(roomId);
            if(roomSessions != null) {
                roomSessions.remove(sessionId);
            }

            boolean isCreator = room.getCreator().getEmail().equals(email);
            if(isCreator) {
                // Reset all votes for songs in the room
                List<Song> roomSongs = songRepository.findByRoomId(roomId);
                for(Song song : roomSongs) {
                    votingService.removeVotes(song.getId());
                    voteRepository.deleteBySong(song);
                    song.setUpvotes(0);
                    if(!song.isCurrent()) {
                        song.setQueuePosition(null);
                    }
                }

                songRepository.saveAll(roomSongs);

                room.setCreatorJoined(false);
                roomRepository.save(room);

                // Clear active sessions
                activeSessionsInRoom.remove(roomId);
            }

            return isCreator;
        } catch (Exception e) {
            throw new RuntimeException("Failed to leave room: " + e.getMessage());
        }
    }
}
