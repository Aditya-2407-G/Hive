package org.vsarthi.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vsarthi.backend.model.Song;
import org.vsarthi.backend.model.Room;
import java.util.Optional;
import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {
    // Modified to include queue position in sorting
    @Query("SELECT s FROM Song s WHERE s.room.id = :roomId ORDER BY s.upvotes DESC, s.queuePosition ASC NULLS LAST")
    List<Song> findByRoomIdOrderByUpvotesDesc(@Param("roomId") Long roomId);

    Optional<Song> findByYoutubeLinkAndRoomId(String youtubeLink, Long roomId);

    Optional<Song> findByRoomIdAndIsCurrent(Long roomId, boolean isCurrent);

    // Modified to include queue position in sorting for non-current songs
    @Query("SELECT s FROM Song s WHERE s.room.id = :roomId AND s.isCurrent = false ORDER BY s.upvotes DESC, s.queuePosition ASC")
    List<Song> findByRoomIdAndIsCurrentFalseOrderByUpvotesDesc(@Param("roomId") Long roomId);
}