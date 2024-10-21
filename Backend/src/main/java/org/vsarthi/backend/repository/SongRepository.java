package org.vsarthi.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vsarthi.backend.model.Song;
import org.vsarthi.backend.model.Room;
import java.util.Optional;
import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {
    List<Song> findByRoomIdOrderByUpvotesDesc(Long roomId);
    // Add this method to check for duplicate YouTube links in a room
    Optional<Song> findByYoutubeLinkAndRoomId(String youtubeLink, Long roomId);
}
