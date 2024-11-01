package org.vsarthi.backend.service;

import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.vsarthi.backend.model.Room;
import org.vsarthi.backend.model.Song;
import org.vsarthi.backend.model.Users;
import org.vsarthi.backend.model.Vote;
import org.vsarthi.backend.repository.SongRepository;
import org.vsarthi.backend.repository.VoteRepository;

import java.util.List;

@Service
@AllArgsConstructor
public class VotingService {

    private final SongRepository songrepository;
    private final VoteRepository voteRepository;
    private final SimpMessagingTemplate messageTemplate;


    public Song vote(Long songId, Users user) {

        Song song = songrepository.findById(songId).orElseThrow(() -> new IllegalArgumentException("Song not found"));

        Room room = song.getRoom();

        boolean isUserInRoom = room.getJoinedUsers().stream().anyMatch(u -> u.getId().equals(user.getId()));

        if(!isUserInRoom) {
            throw new IllegalArgumentException("User not in room");
        }

        // check if user has already voted

        boolean hasVoted = voteRepository.existsBySongIdAndUserId(song.getId(), user.getId());

        if(hasVoted) {
            throw new IllegalArgumentException("User has already voted");
        }

        // create vote
        Vote vote = new Vote();
        vote.setSong(song);
        vote.setUser(user);
        voteRepository.save(vote);

        // update the song table
        song.setUpvotes(song.getUpvotes()  + 1);

        songrepository.save(song);

        List<Song> songs = songrepository.findByRoomId(room.getId());

        messageTemplate.convertAndSend("/topic/room/" + room.getId() + "/songs", songs);

        return song;
    }

    public void removeVotes(Long songId) {
        voteRepository.deleteBySong(songrepository.findById(songId).orElseThrow(() -> new IllegalArgumentException("Song not found")));
    }
}
