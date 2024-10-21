import { useState, useEffect, useCallback } from 'react';
import { Client } from '@stomp/stompjs';

const SOCKET_URL = 'ws://localhost:8080/ws';

export const useWebSocket = (roomId, token) => {
  const [client, setClient] = useState(null);
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    const stompClient = new Client({
      brokerURL: SOCKET_URL,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    stompClient.onConnect = () => {
      console.log('Connected to WebSocket');
      setIsConnected(true);
      setClient(stompClient);

      // Subscribe to room-specific topics
      stompClient.subscribe(`/topic/room/${roomId}/songs`, handleNewSong);
      stompClient.subscribe(`/topic/room/${roomId}/votes`, handleVote);
      stompClient.subscribe(`/topic/room/${roomId}/users`, handleUserJoin);
    };

    stompClient.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
      setIsConnected(false);
    };

    stompClient.onWebSocketError = (event) => {
      console.error('WebSocket error:', event);
      setIsConnected(false);
    };

    stompClient.activate();

    return () => {
      if (stompClient) {
        stompClient.deactivate();
      }
    };
  }, [roomId, token]);

  const handleNewSong = useCallback((message) => {
    const song = JSON.parse(message.body);
    console.log('New song added:', song);
    // Handle new song (e.g., update state)
  }, []);

  const handleVote = useCallback((message) => {
    const updatedSong = JSON.parse(message.body);
    console.log('Vote updated:', updatedSong);
    // Handle vote update (e.g., update state)
  }, []);

  const handleUserJoin = useCallback((message) => {
    console.log('User joined:', message.body);
    // Handle user join (e.g., update user list)
  }, []);

  const sendMessage = useCallback((destination, body) => {
    if (client && isConnected) {
      client.publish({ destination, body: JSON.stringify(body) });
    } else {
      console.error('Cannot send message: client is not connected');
    }
  }, [client, isConnected]);

  return { isConnected, sendMessage };
};