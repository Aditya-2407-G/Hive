package org.vsarthi.backend.contoller;

import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.vsarthi.backend.model.*;
import org.vsarthi.backend.service.CachedVotingService;
import org.vsarthi.backend.service.LeaveRoomMessage;
import org.vsarthi.backend.service.RoomService;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CachedVotingService cachedVotingService;

    @Autowired
    public RoomController(RoomService roomService , SimpMessagingTemplate messagingTemplate, CachedVotingService cachedVotingService) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
        this.cachedVotingService = cachedVotingService;
    }

    @PostMapping
    public ResponseEntity<Room> createRoom(@RequestBody String roomName, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Room room = roomService.createRoom(roomName, userPrincipal.getUser());
        return ResponseEntity.ok(room);
    }

    @GetMapping
    public ResponseEntity<List<Room>> getUserRooms(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<Room> rooms = roomService.getRoomsByUser(userPrincipal.getUser());
        return ResponseEntity.ok(rooms);
    }

    @PostMapping("/{roomId}/songs")
    public ResponseEntity<Song> addSong(@PathVariable Long roomId, @RequestBody Song song, @AuthenticationPrincipal UserPrincipal userPrincipal) throws Exception {
        Song addedSong = roomService.addSongToRoom(roomId, song.getYoutubeLink(), userPrincipal.getUser());
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/songs", roomService.getSongsInRoom(roomId));
        return ResponseEntity.ok(addedSong);
    }

    @GetMapping("/{roomId}/songs")
    public ResponseEntity<List<Song>> getRoomSongs(@PathVariable Long roomId) {
        List<Song> songs = roomService.getSongsInRoom(roomId);
        return ResponseEntity.ok(songs);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<Room> getRoom(@PathVariable Long roomId) {
        Room room = roomService.getRoomDetails(roomId);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/{roomId}/current-song")
    public ResponseEntity<Song> updateCurrentSong(@PathVariable Long roomId, @RequestParam Long songId, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Song updatedSong = roomService.updateCurrentSong(roomId, songId, userPrincipal.getUser());
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/currentSong", updatedSong);
        return ResponseEntity.ok(updatedSong);
    }

    @PostMapping("/songs/{songId}/vote")
    public ResponseEntity<?> voteSong(@PathVariable Long songId, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            Song updatedSong = cachedVotingService.vote(songId, userPrincipal.getUser());
            return ResponseEntity.ok(updatedSong);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{roomId}/generate-shareable-link")
    public ResponseEntity<String> generateShareableLink(@PathVariable Long roomId, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        String shareableLink = roomService.generateShareableLink(roomId, userPrincipal.getUser());
        return ResponseEntity.ok(shareableLink);
    }

    @PostMapping("/join/{shareableLink}")
    public ResponseEntity<Room> joinRoom(@PathVariable String shareableLink, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Room room = roomService.joinRoom(shareableLink, userPrincipal.getUser());
        return ResponseEntity.ok(room);
    }

    @GetMapping("/shareable-link/{shareableLink}")
    public ResponseEntity<Room> getRoomByShareableLink(@PathVariable String shareableLink) {
        Room room = roomService.getRoomByShareableLink(shareableLink);
        return ResponseEntity.ok(room);
    }


    @MessageMapping("/room/{roomId}/join")
    @SendTo("/topic/room/{roomId}/activeUsers")
    public Integer joinRoom(@DestinationVariable Long roomId, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        return roomService.addActiveUser(roomId, sessionId);
    }

    @MessageMapping("/room/{roomId}/leave")
    @SendTo("/topic/room/{roomId}/activeUsers")
    public Integer leaveRoom(
            @DestinationVariable Long roomId,
            @Payload LeaveRoomMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        String sessionId = headerAccessor.getSessionId();
        return roomService.leaveRoom(roomId, sessionId, message.getEmail());
    }

    @PostMapping("/{roomId}/close")
    public String closeRoom(@PathVariable Long roomId, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.closeRoom(roomId, userPrincipal.getUser());
        return "CLOSED";
    }

    @GetMapping("/{roomId}/active-users")
    public ResponseEntity<Integer> getActiveUsers(@PathVariable Long roomId) {
        Integer activeUsers = roomService.getActiveUsersCount(roomId);
        return ResponseEntity.ok(activeUsers);
    }

    @MessageMapping("/room/{roomId}/disconnect")
    public void handleDisconnect(@DestinationVariable Long roomId, @Payload String userId) {
        Integer activeUsers = roomService.removeActiveUser(roomId, userId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/activeUsers", activeUsers);
    }

    @GetMapping("/{roomId}/is-creator")
    public ResponseEntity<Boolean> isCreator(@PathVariable Long roomId, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Boolean isCreator = roomService.isCreator(roomId, userPrincipal.getUser());
        return ResponseEntity.ok(isCreator);
    }

    @PostMapping("/{roomId}/songs/{songId}/ended")
    public ResponseEntity<?> handleSongEnded(@PathVariable Long roomId, @PathVariable Long songId) {
        try {
            SongEndedResponse response = roomService.handleSongEnded(roomId, songId);
            cachedVotingService.clearVoteCache(songId); // Clear vote cache when song ends
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/song-ended", response);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{roomId}/songs/{songId}/play-now")
    public ResponseEntity<Song> playNow(
            @PathVariable Long roomId,
            @PathVariable Long songId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Song song = roomService.playNow(roomId, songId, userPrincipal.getUser());
        return ResponseEntity.ok(song);
    }

    @DeleteMapping("/{roomId}/songs/{songId}/remove")
    public ResponseEntity<?> removeSong(
            @PathVariable Long roomId,
            @PathVariable Long songId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        roomService.removeSong(roomId, songId, userPrincipal.getUser());
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/songs", roomService.getSongsInRoom(roomId));
        return ResponseEntity.ok().build();
    }


}
