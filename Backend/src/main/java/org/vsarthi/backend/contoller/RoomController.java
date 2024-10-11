package org.vsarthi.backend.contoller;

import lombok.Data;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.vsarthi.backend.model.Room;
import org.vsarthi.backend.model.Song;
import org.vsarthi.backend.model.SongRequest;
import org.vsarthi.backend.model.UserPrincipal;
import org.vsarthi.backend.service.RoomService;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public RoomController(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
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
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/songs", addedSong);
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

    @PostMapping("/songs/{songId}/vote")
    public ResponseEntity<Song> voteSong(@PathVariable Long songId, @RequestParam boolean isUpvote, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Song updatedSong = roomService.vote(songId, userPrincipal.getUser(), isUpvote);
        Long roomId = updatedSong.getRoom().getId();
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/votes", updatedSong);
        return ResponseEntity.ok(updatedSong);
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

    @DeleteMapping("/{roomId}/close")
    public ResponseEntity<?> closeRoom(@PathVariable Long roomId, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.closeRoom(roomId, userPrincipal.getUser());
        return ResponseEntity.ok().build();
    }

    // WebSocket endpoints

    //TODO - Check if this is the correct way to implement the WebSocket endpoints

    @MessageMapping("/room/{roomId}/addSong")
    @SendTo("/topic/room/{roomId}/songs")
    public Song addSongWebSocket(
            @DestinationVariable Long roomId,
            @Payload SongRequest songRequest,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) throws Exception {
        return roomService.addSongToRoom(roomId, songRequest.getYoutubeLink(), userPrincipal.getUser());
    }

    @MessageMapping("/room/{roomId}/vote")
    @SendTo("/topic/room/{roomId}/votes")
    public Song voteWebSocket(@DestinationVariable Long roomId, @RequestBody VoteRequest voteRequest, @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return roomService.vote(voteRequest.getSongId(), userPrincipal.getUser(), voteRequest.isUpvote());
    }

    @MessageMapping("/room/{roomId}/join")
    @SendTo("/topic/room/{roomId}/users")
    public String joinRoomWebSocket(
            @DestinationVariable Long roomId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Room room = roomService.getRoomDetails(roomId);
        return userPrincipal.getUser().getUsername() + " joined the room";
    }
}

@Data
class VoteRequest {
    private Long songId;
    private boolean isUpvote;

    public boolean isUpvote() {
        return isUpvote;
    }

    public void setUpvote(boolean upvote) {
        isUpvote = upvote;
    }
}