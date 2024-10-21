import React, { useState, useEffect } from 'react';
import axios from 'axios';

const RoomControls = ({ roomId, isCreator, currentUser }) => {
  const [songs, setSongs] = useState([]);
  const [currentSong, setCurrentSong] = useState(null);
  const [activeUsers, setActiveUsers] = useState([]);
  const [shareableLink, setShareableLink] = useState('');

  useEffect(() => {
    fetchSongs();
    fetchActiveUsers();
    if (isCreator) {
      generateShareableLink();
    }
  }, [roomId, isCreator]);

  const fetchSongs = async () => {
    try {
      const response = await axios.get(`/api/rooms/${roomId}/songs`);
      setSongs(response.data);
    } catch (error) {
      console.error("Failed to fetch songs:", error);
    }
  };

  const fetchActiveUsers = async () => {
    try {
      const response = await axios.get(`/api/rooms/${roomId}/active-users`);
      setActiveUsers(response.data);
    } catch (error) {
      console.error("Failed to fetch active users:", error);
    }
  };

  const updateCurrentSong = async (songId) => {
    if (!isCreator) return;

    try {
      const response = await axios.post(`/api/rooms/${roomId}/current-song?songId=${songId}`);
      setCurrentSong(response.data);
      alert("Current song updated successfully");
    } catch (error) {
      console.error("Failed to update current song:", error);
      alert("Failed to update current song");
    }
  };

  const generateShareableLink = async () => {
    try {
      const response = await axios.post(`/api/rooms/${roomId}/generate-shareable-link`);
      setShareableLink(response.data);
    } catch (error) {
      console.error("Failed to generate shareable link:", error);
    }
  };

  const closeRoom = async () => {
    if (!isCreator) return;

    try {
      await axios.delete(`/api/rooms/${roomId}/close`);
      alert("Room closed successfully");
      // Redirect to home page or room list
    } catch (error) {
      console.error("Failed to close room:", error);
      alert("Failed to close room");
    }
  };

  const voteSong = async (songId, isUpvote) => {
    try {
      await axios.post(`/api/songs/${songId}/vote?isUpvote=${isUpvote}`);
      fetchSongs(); // Refresh the song list after voting
    } catch (error) {
      console.error("Failed to vote:", error);
      alert("Failed to vote");
    }
  };

  return (
    <div className="room-controls">
      <h2>Room Controls</h2>
      {isCreator && (
        <div>
          <h3>Shareable Link</h3>
          <p>{shareableLink}</p>
          <button onClick={closeRoom}>Close Room</button>
        </div>
      )}
      <div>
        <h3>Current Song</h3>
        {currentSong ? (
          <p>{currentSong.title}</p>
        ) : (
          <p>No song currently playing</p>
        )}
      </div>
      <div>
        <h3>Queue</h3>
        {songs.map((song) => (
          <div key={song.id} className="song-item">
            <span>{song.title}</span>
            {isCreator && (
              <button onClick={() => updateCurrentSong(song.id)}>
                Play
              </button>
            )}
            <button onClick={() => voteSong(song.id, true)}>Upvote</button>
            <button onClick={() => voteSong(song.id, false)}>Downvote</button>
          </div>
        ))}
      </div>
      <div>
        <h3>Active Users</h3>
        <ul>
          {activeUsers.map((user) => (
            <li key={user.id}>{user.username}</li>
          ))}
        </ul>
      </div>
    </div>
  );
};

export default RoomControls;