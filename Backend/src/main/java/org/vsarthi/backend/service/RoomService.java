package org.vsarthi.backend.service;

import jakarta.transaction.Transactional;
import org.apache.commons.logging.Log;
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
    private final Map<Long, Set<String>> activeSessionsInRoom = new ConcurrentHashMap<>();
    private SimpMessageSendingOperations messagingTemplate;

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


        return songRepository.save(song);

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

        // Use voteRepository to check for existing vote
        Optional<Vote> existingVote = voteRepository.findByUserIdAndSongId(user.getId(), songId);

        if (existingVote.isPresent()) {
            throw new RuntimeException("User has already voted for this song");
        } else {
            Vote vote = new Vote();
            vote.setSong(song);
            vote.setUser(user);
            vote.setUpvote(isUpvote);
            voteRepository.save(vote);
            updateSongVotes(song, isUpvote ? 1 : -1);
        }

        messagingTemplate.convertAndSend("/topic/room/" + song.getRoom().getId() + "/votes", song);

        return songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found after update"));

    }

    @Transactional
    public void updateSongVotes(Song song, int change) {
        if (change > 0) {
            song.setUpvotes(song.getUpvotes() + change);
        }
        songRepository.save(song);
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

        if(!room.getCreator().getId().equals(user.getId())) {
            throw new RuntimeException("You are not the creator of the room, Only the creator can update the current song");
        }

        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));

        if(!song.getRoom().getId().equals(roomId)) {
            throw new RuntimeException("Song does not belong to the room");
        }

        room.setCurrentSong(song);
        roomRepository.save(room);
        return song;

    }

    @Transactional
    public void closeRoom(Long roomId, Users user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getCreator().getId().equals(user.getId())) {
            throw new RuntimeException("You are not the creator of the room");
        }

        roomRepository.delete(room);
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
    public Integer addActiveUser(Long roomId, String sessionId) {
        Set<String> roomSessions = activeSessionsInRoom.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());
        boolean wasAdded = roomSessions.add(sessionId);
        if (wasAdded) {
            System.out.println("Active user added to room " + roomId + ": " + sessionId);
        } else {
            System.out.println("User already active in room " + roomId + ": " + sessionId);
        }
        return roomSessions.size();
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

        // Remove all votes associated with the song
        voteRepository.deleteBySong(endedSong);
        // Reset votes
        endedSong.setUpvotes(0);

        // Clear the votes list in the Song entity
        endedSong.setVotes(new ArrayList<>());

        // Move the ended song to the end of the queue
        List<Song> songs = getSongsInRoom(roomId);
        songs.remove(endedSong);
        songs.add(endedSong);

        // Update the order of songs
        for (int i = 0; i < songs.size(); i++) {
            songs.get(i).setQueuePosition(i);
        }

        songRepository.saveAll(songs);

        // Prepare the response
        List<Long> newSongOrder = songs.stream().map(Song::getId).toList();
        return new SongEndedResponse(songId, newSongOrder);
    }
}
