package org.vsarthi.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.vsarthi.backend.DTO.PlaylistRequest;
import org.vsarthi.backend.model.Playlists;
import org.vsarthi.backend.model.PlaylistSongs;
import org.vsarthi.backend.model.SongRequest;
import org.vsarthi.backend.model.UserPrincipal;
import org.vsarthi.backend.service.PlaylistService;
import java.util.List;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {
    private final PlaylistService playlistService;

    @Autowired
    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @PostMapping
    public ResponseEntity<Playlists> createPlaylist(
            @RequestBody PlaylistRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        Playlists playlist = playlistService.createPlaylist(
                request.getName(),
                request.getDescription(),
                request.getGenre(),
                userPrincipal.getUser()
        );
        return ResponseEntity.ok(playlist);
    }

    @GetMapping
    public ResponseEntity<List<Playlists>> getAllPlaylists(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        List<Playlists> playlists = playlistService.getAllPlaylists();
        return ResponseEntity.ok(playlists);
    }

    @PostMapping("/{playlistId}/songs")
    public ResponseEntity<PlaylistSongs> addSongToPlaylist(
            @PathVariable Long playlistId,
            @RequestBody SongRequest songRequest,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) throws Exception {
        PlaylistSongs song = playlistService.addSongToPlaylist(
                playlistId, songRequest.getYoutubeLink(), userPrincipal.getUser()
        );
        return ResponseEntity.ok(song);
    }

    @GetMapping("/genre/{genre}")
    public ResponseEntity<List<Playlists>> getPlaylistsByGenre(@PathVariable String genre) {
        List<Playlists> playlists = playlistService.getPlaylistsByGenre(genre);
        return ResponseEntity.ok(playlists);
    }

    @GetMapping("/{playlistId}/songs")
    public ResponseEntity<List<PlaylistSongs>> getPlaylistSongs(@PathVariable Long playlistId) {
        List<PlaylistSongs> songs = playlistService.getPlaylistSongs(playlistId);
        return ResponseEntity.ok(songs);
    }


    @DeleteMapping("/{playlistId}")
    public ResponseEntity<Void> deletePlaylist(@PathVariable Long playlistId, @AuthenticationPrincipal UserPrincipal userPrincipal) {

        playlistService.deletePlaylist(playlistId, userPrincipal.getUser());
        return ResponseEntity.noContent().build();
    }



}