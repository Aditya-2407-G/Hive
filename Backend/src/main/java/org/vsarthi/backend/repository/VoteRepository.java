package org.vsarthi.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vsarthi.backend.model.Song;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.model.Vote;

public interface VoteRepository extends JpaRepository<Vote, Long> {



    void deleteBySong(Song endedSong);

    Optional<Vote> findByUserIdAndSongId(Long userId, Long songId);

    Vote findBySongIdAndUser(Long songId, Users user);

    boolean existsBySongIdAndUserId(Long songId, Long userId);
    List<Vote> findBySongId(Long songId);
    long countBySongId(Long songId);
}