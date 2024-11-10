package org.vsarthi.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.vsarthi.backend.model.PlaylistSongs;
import org.vsarthi.backend.model.Playlists;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.repository.PlaylistRepository;
import org.vsarthi.backend.repository.PlaylistSongRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PlaylistService {
    private static final Logger logger = LoggerFactory.getLogger(PlaylistService.class);
    private static final String PLAYLIST_GENRE_KEY = "playlists:genre:";
    private static final String PLAYLIST_SONGS_KEY = "playlist:songs:";
    private static final String ALL_PLAYLISTS_KEY = "playlists:all";

    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final YouTubeService youTubeService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PlaylistService(PlaylistRepository playlistRepository,
                           PlaylistSongRepository playlistSongRepository,
                           RedisTemplate<String, Object> redisTemplate,
                           YouTubeService youTubeService,
                           ObjectMapper objectMapper) {
        this.playlistRepository = playlistRepository;
        this.playlistSongRepository = playlistSongRepository;
        this.redisTemplate = redisTemplate;
        this.youTubeService = youTubeService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Playlists createPlaylist(String name, String description, String genre, Users creator) {
        Playlists playlist = new Playlists();
        playlist.setName(name);
        playlist.setDescription(description);
        playlist.setGenre(genre);
        playlist.setCreator(creator);
        Playlists savedPlaylist = playlistRepository.save(playlist);

        clearCacheForGenre(genre);
        clearAllPlaylistsCache();

        return savedPlaylist;
    }

    @Transactional
    public PlaylistSongs addSongToPlaylist(Long playlistId, String youtubeLink, Users user) throws Exception {
        // Find the playlist
        Playlists playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        // Validate and extract YouTube video information
        String videoId = youTubeService.extractVideoId(youtubeLink);
        if (!youTubeService.isVideoAvailable(videoId)) {
            throw new RuntimeException("Video is not available or not embeddable");
        }

        String title = youTubeService.getVideoTitle(videoId);
        if (title == null || title.trim().isEmpty()) {
            throw new RuntimeException("Could not fetch video title");
        }

        // Create and save the new song
        PlaylistSongs song = new PlaylistSongs();
        song.setPlaylist(playlist);
        song.setSongName(title);
        song.setYoutubeLink(youtubeLink);
        song.setAddedBy(user);
        song.setPosition(playlist.getSongs().size() + 1);

        PlaylistSongs savedSong = playlistSongRepository.save(song);

        // Clear relevant caches
        clearPlaylistCache(playlistId);
        clearCacheForGenre(playlist.getGenre());
        clearAllPlaylistsCache();

        return savedSong;
    }

    public List<Playlists> getPlaylistsByGenre(String genre) {
        String cacheKey = PLAYLIST_GENRE_KEY + genre;
        try {
            String cachedPlaylists = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cachedPlaylists != null) {
                return objectMapper.readValue(cachedPlaylists,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Playlists.class));
            }
        } catch (Exception e) {
            logger.error("Error retrieving playlists by genre from cache", e);
        }

        List<Playlists> playlists = playlistRepository.findByGenre(genre);
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(playlists),
                    1,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            logger.error("Error caching playlists by genre", e);
        }

        return playlists;
    }

    public List<PlaylistSongs> getPlaylistSongs(Long playlistId) {
        String cacheKey = PLAYLIST_SONGS_KEY + playlistId;
        try {
            String cachedSongs = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cachedSongs != null) {
                return objectMapper.readValue(cachedSongs,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PlaylistSongs.class));
            }
        } catch (Exception e) {
            logger.error("Error retrieving playlist songs from cache", e);
        }

        List<PlaylistSongs> songs = playlistSongRepository.findByPlaylistIdOrderByPositionAsc(playlistId);
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(songs),
                    1,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            logger.error("Error caching playlist songs", e);
        }

        return songs;
    }

    public List<Playlists> getAllPlaylists() {
        try {
            String cachedPlaylists = (String) redisTemplate.opsForValue().get(ALL_PLAYLISTS_KEY);
            if (cachedPlaylists != null) {
                return objectMapper.readValue(cachedPlaylists,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Playlists.class));
            }
        } catch (Exception e) {
            logger.error("Error retrieving all playlists from cache", e);
        }

        List<Playlists> playlists = playlistRepository.findAll();
        try {
            redisTemplate.opsForValue().set(
                    ALL_PLAYLISTS_KEY,
                    objectMapper.writeValueAsString(playlists),
                    1,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            logger.error("Error caching all playlists", e);
        }

        return playlists;
    }

    @Transactional
    public void deletePlaylist(Long playlistId, Users user) {
        Playlists playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        if (!playlist.getCreator().getId().equals(user.getId())) {
            throw new RuntimeException("Only playlist creator can delete the playlist");
        }

        String genre = playlist.getGenre();

        playlistRepository.delete(playlist);

        clearPlaylistCache(playlistId);
        clearCacheForGenre(genre);
        clearAllPlaylistsCache();
    }

    private void clearPlaylistCache(Long playlistId) {
        try {
            redisTemplate.delete(PLAYLIST_SONGS_KEY + playlistId);
        } catch (Exception e) {
            logger.error("Error clearing playlist cache for ID: " + playlistId, e);
        }
    }

    private void clearCacheForGenre(String genre) {
        try {
            redisTemplate.delete(PLAYLIST_GENRE_KEY + genre);
        } catch (Exception e) {
            logger.error("Error clearing cache for genre: " + genre, e);
        }
    }

    private void clearAllPlaylistsCache() {
        try {
            redisTemplate.delete(ALL_PLAYLISTS_KEY);
        } catch (Exception e) {
            logger.error("Error clearing all playlists cache", e);
        }
    }
}