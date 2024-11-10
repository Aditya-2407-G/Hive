package org.vsarthi.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vsarthi.backend.model.PlaylistSongs;
import java.util.List;

public interface PlaylistSongRepository extends JpaRepository<PlaylistSongs, Long> {
    List<PlaylistSongs> findByPlaylistIdOrderByPositionAsc(Long playlistId);
}