package org.vsarthi.backend.service;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.vsarthi.backend.model.Room;
import org.vsarthi.backend.model.Song;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.model.Vote;
import org.vsarthi.backend.repository.SongRepository;
import org.vsarthi.backend.repository.VoteRepository;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
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

    private final BlockingQueue<VoteRequest> voteQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        startVoteProcessor();
    }

    private void startVoteProcessor() {
        Thread processor = new Thread(() -> {
            while (true) {
                try {
                    VoteRequest request = voteQueue.take();
                    processVoteRequest(request);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error processing vote: ", e);
                }
            }
        });
        processor.setDaemon(true);
        processor.start();
    }

    @Retryable(
            value = {OptimisticLockingFailureException.class, DataAccessException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Song vote(Long songId, Users user) {
        log.info("Queueing vote for song {} by user {}", songId, user.getId());
        CompletableFuture<Song> future = new CompletableFuture<>();
        voteQueue.offer(new VoteRequest(songId, user, future));
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process vote: " + e.getMessage());
        }
    }

    private void processVoteRequest(VoteRequest request) {
        try {
            String voteCountKey = getVoteCountKey(request.songId);
            String votersSetKey = getVotersSetKey(request.songId);

            Song song = validateAndGetSong(request.songId, request.user);

            // First, try to add the user to the Redis set
            Long added = redisTemplate.opsForSet().add(votersSetKey, request.user.getId().toString());
            if (added == 0) {
                throw new RuntimeException("User has already voted for this song");
            }

            try {
                // Get current count from Redis or initialize it
                String currentValue = (String) redisTemplate.opsForValue().get(voteCountKey);
                int currentCount = 0;
                
                if (currentValue != null) {
                    try {
                        currentCount = Integer.parseInt(currentValue);
                    } catch (NumberFormatException e) {
                        log.error("Invalid vote count in Redis for song {}: {}", request.songId, currentValue);
                        // Reset the value if it's invalid
                        currentCount = 0;
                    }
                }
                
                // Increment count
                int newCount = currentCount + 1;
                redisTemplate.opsForValue().set(voteCountKey, String.valueOf(newCount));

                // Update song upvotes to match Redis
                song.setUpvotes(newCount);
                songRepository.saveAndFlush(song);

                // Save vote to DB
                Vote vote = new Vote();
                vote.setSong(song);
                vote.setUser(request.user);
                vote.setUpvote(true);
                voteRepository.saveAndFlush(vote);

                // Get all songs in room to maintain proper order
                List<Song> updatedSongs = songRepository.findByRoomIdOrderByUpvotesDesc(song.getRoom().getId());

                // Broadcast update
                messagingTemplate.convertAndSend(
                    "/topic/room/" + song.getRoom().getId() + "/songs",
                    updatedSongs
                );

                request.future.complete(song);
            } catch (Exception e) {
                // If anything fails after adding to set, remove the user from voters set
                redisTemplate.opsForSet().remove(votersSetKey, request.user.getId().toString());
                throw e;
            }
        } catch (Exception e) {
            log.error("Vote processing failed for song {} user {}: {}", 
                request.songId, request.user.getId(), e.getMessage(), e);
            request.future.completeExceptionally(e);
        }
    }

    @Data
    @AllArgsConstructor
    private static class VoteRequest {
        private final Long songId;
        private final Users user;
        private final CompletableFuture<Song> future;
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

    private String getVoteCountKey(Long songId) {
        return VOTE_COUNT_PREFIX + songId;
    }

    private String getVotersSetKey(Long songId) {
        return VOTERS_SET_PREFIX + songId;
    }

    public void clearVoteCache(Long songId) {
        log.info("Clearing vote cache for song {}", songId);
        String voteCountKey = getVoteCountKey(songId);
        String votersSetKey = getVotersSetKey(songId);

        try {
            Boolean deletedCount = redisTemplate.delete(voteCountKey);
            Boolean deletedVoters = redisTemplate.delete(votersSetKey);
            log.info("Cleared vote cache for song {}: count={}, voters={}",
                    songId, deletedCount, deletedVoters);
        } catch (Exception e) {
            log.error("Error clearing vote cache for song {}: {}", songId, e.getMessage(), e);
        }
    }

    public boolean hasVoted(Long songId, Users user) {
        String votersSetKey = getVotersSetKey(songId);
        Boolean redisVote = redisTemplate.opsForSet().isMember(votersSetKey, user.getId().toString());
        boolean dbVote = voteRepository.existsBySongIdAndUserId(songId, user.getId());

        log.info("Vote status for song {} user {}: Redis={}, DB={}",
                songId, user.getId(), redisVote, dbVote);

        return Boolean.TRUE.equals(redisVote) || dbVote;
    }

    @Scheduled(fixedRate = 5000) // Run every 5 seconds
    public void syncVoteCounts() {
        try {
            List<Song> allSongs = songRepository.findAll();
            for (Song song : allSongs) {
                String voteCountKey = getVoteCountKey(song.getId());
                Object redisValue = redisTemplate.opsForValue().get(voteCountKey);

                Long redisCount = null;
                if (redisValue != null) {
                    if (redisValue instanceof String) {
                        redisCount = Long.parseLong((String) redisValue);
                    } else if (redisValue instanceof Number) {
                        redisCount = ((Number) redisValue).longValue();
                    }
                }

                Long dbCount = voteRepository.countBySongId(song.getId());

                if (redisCount == null || !redisCount.equals(dbCount)) {
                    // Update Redis to match DB
                    redisTemplate.opsForValue().set(voteCountKey, String.valueOf(dbCount));
                    song.setUpvotes(dbCount.intValue());
                    songRepository.save(song);

                    // Broadcast update
                    List<Song> updatedSongs = songRepository.findByRoomIdOrderByUpvotesDesc(song.getRoom().getId());
                    messagingTemplate.convertAndSend(
                        "/topic/room/" + song.getRoom().getId() + "/songs",
                        updatedSongs
                    );
                }
            }
        } catch (Exception e) {
            log.error("Error syncing vote counts: ", e);
        }
    }

    // Add this method to initialize Redis for a song when it's added
    public void initializeSongInRedis(Long songId) {
        String voteCountKey = getVoteCountKey(songId);
        String votersSetKey = getVotersSetKey(songId);
        
        try {
            // Initialize vote count
            Long dbCount = voteRepository.countBySongId(songId);
            redisTemplate.opsForValue().set(voteCountKey, String.valueOf(dbCount));
            
            // Initialize voters set
            List<Vote> existingVotes = voteRepository.findBySongId(songId);
            if (!existingVotes.isEmpty()) {
                String[] voterIds = existingVotes.stream()
                    .map(vote -> vote.getUser().getId().toString())
                    .toArray(String[]::new);
                redisTemplate.opsForSet().add(votersSetKey, (Object[]) voterIds);
            }
            
            log.info("Initialized Redis for song {}: count={}, voters={}", 
                songId, dbCount, existingVotes.size());
        } catch (Exception e) {
            log.error("Failed to initialize Redis for song {}: {}", songId, e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Redis", e);
        }
    }
}