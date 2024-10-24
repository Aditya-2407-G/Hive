package org.vsarthi.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vsarthi.backend.model.Room;
import org.vsarthi.backend.model.Song;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.model.Vote;
import org.vsarthi.backend.repository.SongRepository;
import org.vsarthi.backend.repository.VoteRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CachedVotingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SongRepository songRepository;
    private final VoteRepository voteRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final long VOTE_CACHE_DURATION = 24; // hours
    private static final String VOTE_COUNT_PREFIX = "song:votes:count:";
    private static final String VOTERS_SET_PREFIX = "song:votes:users:";

    private String getVoteCountKey(Long songId) {
        return VOTE_COUNT_PREFIX + songId;
    }

    private String getVotersSetKey(Long songId) {
        return VOTERS_SET_PREFIX + songId;
    }

    @Transactional
    public Song vote(Long songId, Users user) {
        log.info("Processing vote for song {} by user {}", songId, user.getId());

        // First check if vote exists in database
        if (voteRepository.existsBySongIdAndUserId(songId, user.getId())) {
            log.warn("Vote already exists in database for song {} and user {}", songId, user.getId());
            throw new RuntimeException("User has already voted for this song");
        }

        // Validate song and user
        Song song = validateAndGetSong(songId, user);

        String voteCountKey = getVoteCountKey(songId);
        String votersSetKey = getVotersSetKey(songId);

        SetOperations<String, Object> setOps = redisTemplate.opsForSet();
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

        try {
            // Initialize Redis keys if they don't exist
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(votersSetKey))) {
                log.info("Initializing voters set for song {}", songId);
                // Initialize from database
                List<Vote> existingVotes = voteRepository.findBySongId(songId);
                existingVotes.forEach(vote ->
                        setOps.add(votersSetKey, vote.getUser().getId().toString())
                );
                redisTemplate.expire(votersSetKey, VOTE_CACHE_DURATION, TimeUnit.HOURS);
            }

            if (!Boolean.TRUE.equals(redisTemplate.hasKey(voteCountKey))) {
                log.info("Initializing vote count for song {}", songId);
                long voteCount = voteRepository.countBySongId(songId);
                valueOps.set(voteCountKey, String.valueOf(voteCount));
                redisTemplate.expire(voteCountKey, VOTE_CACHE_DURATION, TimeUnit.HOURS);
            }

            // Try to add user to voters set
            Long added = setOps.add(votersSetKey, user.getId().toString());
            log.info("Add to voters set result for song {} and user {}: {}", songId, user.getId(), added);

            if (Boolean.FALSE.equals(added)) {
                log.warn("User {} already in Redis voters set for song {}", user.getId(), songId);
                throw new RuntimeException("User has already voted for this song");
            }

            // Save vote to database
            Vote vote = new Vote();
            vote.setSong(song);
            vote.setUser(user);
            vote.setUpvote(true);
            voteRepository.save(vote);
            log.info("Vote saved to database for song {} and user {}", songId, user.getId());

            // Increment vote count in Redis
            Long newVoteCount = valueOps.increment(voteCountKey);
            if (newVoteCount == null) {
                newVoteCount = 1L;
                valueOps.set(voteCountKey, String.valueOf(newVoteCount));
            }
            log.info("New vote count for song {}: {}", songId, newVoteCount);

            // Update song's vote count
            song.setUpvotes(newVoteCount.intValue());
            song = songRepository.save(song);

            // Notify clients
            notifyVoteUpdate(song);

            return song;

        } catch (Exception e) {
            log.error("Error processing vote for song {} and user {}: {}", songId, user.getId(), e.getMessage());
            // Cleanup Redis if vote failed
            setOps.remove(votersSetKey, user.getId().toString());
            throw e;
        }
    }

    private Song validateAndGetSong(Long songId, Users user) {
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

    @Scheduled(fixedRate = 30000)
    public void synchronizeVotes() {
        log.debug("Starting vote synchronization");
        List<Song> songs = songRepository.findAll();
        for (Song song : songs) {
            synchronizeSongVotes(song.getId());
        }
    }

    private void synchronizeSongVotes(Long songId) {
        try {
            String voteCountKey = getVoteCountKey(songId);
            Object cachedVotes = redisTemplate.opsForValue().get(voteCountKey);

            if (cachedVotes != null) {
                int voteCount = Integer.parseInt(cachedVotes.toString());
                songRepository.findById(songId).ifPresent(song -> {
                    if (song.getUpvotes() != voteCount) {
                        song.setUpvotes(voteCount);
                        songRepository.save(song);
                        log.debug("Synchronized votes for song {}: {}", songId, voteCount);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error synchronizing votes for song {}: {}", songId, e.getMessage());
        }
    }

    private void notifyVoteUpdate(Song song) {
        List<Song> updatedSongs = songRepository.findByRoomIdOrderByUpvotesDesc(song.getRoom().getId());
        messagingTemplate.convertAndSend(
                "/topic/room/" + song.getRoom().getId() + "/songs",
                updatedSongs
        );
    }

    public void clearVoteCache(Long songId) {
        log.info("Clearing vote cache for song {}", songId);
        String voteCountKey = getVoteCountKey(songId);
        String votersSetKey = getVotersSetKey(songId);
        redisTemplate.delete(voteCountKey);
        redisTemplate.delete(votersSetKey);
    }
}