package org.vsarthi.backend.service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.vsarthi.backend.model.Room;
import org.vsarthi.backend.model.Song;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.model.Vote;
import org.vsarthi.backend.repository.SongRepository;
import org.vsarthi.backend.repository.VoteRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CachedVotingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SongRepository songRepository;
    private final VoteRepository voteRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String VOTE_COUNT_PREFIX = "song:votes:count:";
    private static final String VOTERS_SET_PREFIX = "song:votes:users:";

    private String getVoteCountKey(Long songId) {
        return VOTE_COUNT_PREFIX + songId;
    }

    private String getVotersSetKey(Long songId) {
        return VOTERS_SET_PREFIX + songId;
    }

    @Retryable(
            value = {OptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Song vote(Long songId, Users user) {
        log.info("Processing vote for song {} by user {}", songId, user.getId());

        String voteCountKey = getVoteCountKey(songId);
        String votersSetKey = getVotersSetKey(songId);
        SetOperations<String, Object> setOps = redisTemplate.opsForSet();

        // Get the song first for room information
        Song song = validateAndGetSong(songId, user);

        // Check for existing vote
        if (Boolean.TRUE.equals(setOps.isMember(votersSetKey, user.getId().toString())) ||
                voteRepository.existsBySongIdAndUserId(songId, user.getId())) {

            log.warn("Vote found for song {} and user {}", songId, user.getId());
            // Send refresh signal even when vote exists
            notifyVoteUpdate(song);
            throw new RuntimeException("User has already voted for this song");
        }

        try {
            // Save vote to database
            Vote vote = new Vote();
            vote.setSong(song);
            vote.setUser(user);
            vote.setUpvote(true);
            voteRepository.saveAndFlush(vote);  // Use saveAndFlush to ensure immediate persistence

            // Update song's vote count
            song.setUpvotes(song.getUpvotes() + 1);
            song = songRepository.saveAndFlush(song);  // Immediate persistence

            // Update Redis cache after successful database update
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.multi();  // Start Redis transaction
                try {
                    setOps.add(votersSetKey, user.getId().toString());
                    redisTemplate.opsForValue().increment(voteCountKey);
                    connection.exec();  // Commit Redis transaction
                } catch (Exception e) {
                    connection.discard();  // Rollback Redis transaction
                    throw e;
                }
                return null;
            });

            // Send real-time update
            notifyVoteUpdate(song);

            return song;

        } catch (Exception e) {
            log.error("Error processing vote for song {} and user {}: {}", songId, user.getId(), e.getMessage());
            // Clean up Redis in case of partial updates
            setOps.remove(votersSetKey, user.getId().toString());
            redisTemplate.opsForValue().decrement(voteCountKey);
            // Send refresh signal even on error
            notifyVoteUpdate(song);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    protected Song validateAndGetSong(Long songId, Users user) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));

        if (song.isCurrent()) {
            throw new RuntimeException("Cannot vote on currently playing song");
        }

        Room room = song.getRoom();
        boolean userJoined = room.getJoinedUsers().stream()
                .anyMatch(joinedUser -> joinedUser.getId().equals(user.getId()));

        if (!userJoined) {
            throw new RuntimeException("User has not joined the room");
        }

        return song;
    }

    private void notifyVoteUpdate(Song song) {
        // Get fresh list of songs
        List<Song> updatedSongs = songRepository.findByRoomIdOrderByUpvotesDesc(song.getRoom().getId());
        messagingTemplate.convertAndSend(
                "/topic/room/" + song.getRoom().getId() + "/songs",
                updatedSongs
        );
    }

    public void clearVoteCache(Long songId) {
        log.info("Clearing vote cache for song {}", songId);
        redisTemplate.delete(getVoteCountKey(songId));
        redisTemplate.delete(getVotersSetKey(songId));
    }
}