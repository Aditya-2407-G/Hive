package org.vsarthi.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vsarthi.backend.model.Song;
import org.vsarthi.backend.model.Vote;

import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {



    void deleteBySong(Song endedSong);

    Optional<Vote> findByUserIdAndSongId(Long userId, Long songId);

}