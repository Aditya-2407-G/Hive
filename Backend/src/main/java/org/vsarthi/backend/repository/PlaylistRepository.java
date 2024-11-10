package org.vsarthi.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vsarthi.backend.model.Playlists;
import java.util.List;

public interface PlaylistRepository extends JpaRepository<Playlists, Long> {
    List<Playlists> findByCreatorId(Long creatorId);
    List<Playlists> findByGenre(String genre);
}