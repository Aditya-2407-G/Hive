import React, { useEffect, useState, useRef } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Loader, Share2, Music, Users } from "lucide-react"
import { useApi } from "@/hooks/api";
import { useAuth } from "@/context/AuthProvider";
import { useToast } from "@/hooks/use-toast";
import SyncedPlayer from "./SyncedPlayer";
import AddSongForm from "./AddSongForm";
import SongQueue from "./SongQueue";
import RoomHeader from "./RoomHeader";
import PlaylistTab from "./Playlist/PlaylistTab";

export default function RoomSongs() {
    const { roomId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    const api = useApi();
    const { auth } = useAuth();
    const { toast } = useToast();

    const [songs, setSongs] = useState([]);
    const [currentSong, setCurrentSong] = useState(null);
    const [queuedSongs, setQueuedSongs] = useState([]);
    const [isCreator, setIsCreator] = useState(false);
    const [client, setClient] = useState(null);
    const [loading, setLoading] = useState(true);
    const [activeUsers, setActiveUsers] = useState(0);
    const [youtubeLink, setYoutubeLink] = useState("");
    const [isAddingSong, setIsAddingSong] = useState(false);
    const [loadingIds, setLoadingIds] = useState({
        vote: [],
        playNow: [],
        delete: []
    });
    const subscriptionsRef = useRef({});
    const isEndingRef = useRef(false);
    const { roomName, shareableLink } = location.state || {};

    const addLoadingId = (type, id) => {
        setLoadingIds(prev => ({
            ...prev,
            [type]: [...prev[type], id]
        }));
    };

    const removeLoadingId = (type, id) => {
        setLoadingIds(prev => ({
            ...prev,
            [type]: prev[type].filter(loadingId => loadingId !== id)
        }));
    };

    useEffect(() => {
        fetchRoomData();
        checkIfCreator();
        setupWebSocket();

        return () => {
            cleanupWebSocket();
        };
    }, [roomId]);

    const cleanupWebSocket = () => {
        // Unsubscribe from all topics
        Object.values(subscriptionsRef.current).forEach(subscription => {
            if (subscription && subscription.unsubscribe) {
                subscription.unsubscribe();
            }
        });

        // Clear subscriptions
        subscriptionsRef.current = {};

        // Notify about leaving if client is connected
        if (client && client.connected) {
            client.publish({
                destination: `/app/room/${roomId}/leave`,
                body: JSON.stringify({ email: auth.email }),
            });
            client.deactivate();
        }
    };


    const fetchRoomData = async () => {
        try {
            setLoading(true);
            const songsResponse = await api.get(`/rooms/${roomId}/songs`);
            updateSongsList(songsResponse.data);
        } catch (error) {
            console.error("Error fetching room data:", error);
            toast({
                title: "Error",
                description: "Failed to fetch room data. Please try again.",
                variant: "destructive",
            });
        } finally {
            setLoading(false);
        }
    };

    const checkIfCreator = async () => {
        try {
            const response = await api.get(`/rooms/${roomId}/is-creator`);
            setIsCreator(response.data);
        } catch (error) {
            console.error("Error checking creator status:", error);
        }
    };

    const setupWebSocket = () => {
        const socket = new SockJS(`${import.meta.env.VITE_BASE_URL}/ws`);
        const stompClient = new Client({
            webSocketFactory: () => socket,
            reconnectDelay: 5000,
            debug: (str) => console.log(str),
        });

        stompClient.onConnect = () => {
            console.log("Connected to WebSocket");
            setupSubscriptions(stompClient);
        };

        stompClient.activate();
        setClient(stompClient);
    };

    const setupSubscriptions = (stompClient) => {

        //songs subscription
        subscriptionsRef.current.songs = stompClient.subscribe(
            `/topic/room/${roomId}/songs`,
            (message) => {
                const updatedSongs = JSON.parse(message.body);
                updateSongsList(updatedSongs);
            }
        );

        // Room status subscription
        subscriptionsRef.current.status = stompClient.subscribe(
            `/topic/room/${roomId}/status`,
            (message) => {
                if (message.body === "CLOSED" || message.body === "CREATOR_LEFT") {
                    handleRoomClosure(message.body);
                }
            }
        );

        // Vote update subscription
        subscriptionsRef.current.voteUpdate = stompClient.subscribe(
            `/topic/room/${roomId}/vote-update`,
            (message) => {
                const voteUpdate = JSON.parse(message.body);
                handleVoteUpdate(voteUpdate);
            }
        );

        // Votes subscription
        subscriptionsRef.current.votes = stompClient.subscribe(
            `/topic/room/${roomId}/votes`,
            (message) => {
                const updatedSong = JSON.parse(message.body);
                updateSongVotes(updatedSong);
            }
        );

        // Song ended subscription
        subscriptionsRef.current.songEnded = stompClient.subscribe(
            `/topic/room/${roomId}/song-ended`,
            (message) => {
                const { endedSongId, newSongOrder } = JSON.parse(message.body);
                handleSongEnded(endedSongId, newSongOrder);
            }
        );

        // Active users subscription
        subscriptionsRef.current.activeUsers = stompClient.subscribe(
            `/topic/room/${roomId}/activeUsers`,
            (message) => {
                setActiveUsers(parseInt(message.body));
            }
        );


        // Notify server about joining
        stompClient.publish({
            destination: `/app/room/${roomId}/join`,
            body: JSON.stringify({}),
        });


    };

    const updateSongsList = (songsList) => {
        const current = songsList.find((song) => song.current);
        const queued = songsList
            .filter((song) => !song.current)
            .sort((a, b) => b.upvotes - a.upvotes);

        setCurrentSong(current || null);
        setQueuedSongs(queued);
        setSongs(songsList);
    };

    const updateSongVotes = (updatedSong) => {
        setSongs((prevSongs) =>
            prevSongs
                .map((song) =>
                    song.id === updatedSong.id ? updatedSong : song
                )
                .sort((a, b) => b.upvotes - a.upvotes)
        );
    };

    const handleSongEnded = (endedSongId, newSongOrder) => {
        setSongs((prevSongs) => {
            const updatedSongs = newSongOrder
                .map((id) => {
                    const song = prevSongs.find((s) => s.id === id);
                    if (!song) return null;
                    return {
                        ...song,
                        upvotes: song.id === endedSongId ? 0 : song.upvotes,
                        current: id === newSongOrder[0],
                    };
                })
                .filter(Boolean);
            updateSongsList(updatedSongs);
            return updatedSongs;
        });
    };

    const handleRoomClosure = (reason) => {
        const message =
            reason === "CREATOR_LEFT"
                ? "The room creator has left. You will be redirected to the home page."
                : "The room has been closed. You will be redirected to the home page.";

        toast({
            title: "Room Closed",
            description: message,
            variant: "destructive",
        });

        setTimeout(() => {
            navigate("/home");
        }, 2000);
    };

    const handleVoteUpdate = (update) => {
        const updatedSongs = update.updatedSongs;
        updateSongsList(updatedSongs);
        addLoadingId("vote", update.songId);
    };

    const handleVote = async (songId) => {
        try {
            addLoadingId("vote", songId);
            const response = await api.post(`/rooms/songs/${songId}/vote`);
            const updatedSong = response.data;
            setSongs((prevSongs) =>
                prevSongs.map((song) =>
                    song.id === updatedSong.id
                        ? { ...song, upvotes: updatedSong.upvotes }
                        : song
                )
            );
            toast({
                title: "Success",
                description: "Vote submitted successfully!",
            });
        } catch (error) {
            console.error("Error voting on song:", error);
            toast({
                title: "Error",
                description:
                    error.response?.data.error || "Failed to vote on song",
                variant: "destructive",
            });
        } finally {
            removeLoadingId("vote", songId);
        }
    };

    const handleAddToQueue = async (song) => {
        try {
            await api.post(`/rooms/${roomId}/songs`, { youtubeLink: song.youtubeLink });
            toast({
                title: "Success",
                description: "Song added to queue successfully!",
            });
            // Update the local state instead of fetching all data again
            setSongs(prevSongs => [...prevSongs, song]);
            setQueuedSongs(prevQueued => [...prevQueued, song]);
        } catch (error) {
            console.error("Error adding song to queue:", error);
            toast({
                title: "Error",
                description: error.response?.data.error || "Failed to add song to queue",
                variant: "destructive",
            });
        }
    };

    const generateShareableLink = async () => {
        try {
            const linkToShare =
                shareableLink || `${window.location.origin}/join/${roomId}`;
            await navigator.clipboard.writeText(linkToShare);
            toast({
                title: "Link Copied!",
                description:
                    "Shareable link has been copied to your clipboard.",
            });
        } catch (error) {
            console.error("Error generating shareable link:", error);
            toast({
                title: "Error",
                description:
                    "Failed to generate shareable link. Please try again.",
                variant: "destructive",
            });
        }
    };

    const handlePlayNow = async (songId) => {
        try {
            addLoadingId("playNow", songId);
            await api.post(`/rooms/${roomId}/songs/${songId}/play-now`);
        } catch (error) {
            console.error("Error playing song now:", error);
            toast({
                title: "Error",
                description:
                    error.response?.data.error || "Failed to play song",
                variant: "destructive",
            });
        } finally {
            removeLoadingId("playNow", songId);
        }
    };

    const handleSongDelete = async (songId) => {
        try {
            addLoadingId("delete", songId);
            await api.delete(`/rooms/${roomId}/songs/${songId}/remove`);
            toast({
                title: "Success",
                description: "Song removed successfully!",
            });
        } catch (error) {
            console.error("Error deleting song:", error);
            toast({
                title: "Error",
                description:
                    error.response?.data.error || "Failed to delete song",
                variant: "destructive",
            });
        } finally {
            removeLoadingId("delete", songId);
        }
    };

    const onSongEnd = async () => {
        if (currentSong && !isEndingRef.current) {
            try {
                isEndingRef.current = true;  // Prevent multiple calls
                await api.post(`/rooms/${roomId}/songs/${currentSong.id}/ended`);
            } catch (error) {
                console.error("Error handling song end:", error);
                toast({
                    title: "Error",
                    description: "Failed to update song queue",
                    variant: "destructive",
                });
            } finally {
                // Reset after a delay to prevent rapid subsequent calls
                setTimeout(() => {
                    isEndingRef.current = false;
                }, 1000);
            }
        }
    };

    const addSong = async () => {
        if (!youtubeLink) {
            toast({
                title: "Error",
                description: "Please enter a YouTube link",
                variant: "destructive",
            });
            return;
        }

        setIsAddingSong(true);
        try {
            await api.post(
                `/rooms/${roomId}/songs`,
                { youtubeLink },
                { headers: { Authorization: `Bearer ${auth.accessToken}` } }
            );
            setYoutubeLink("");
            toast({
                title: "Success",
                description: "Song added successfully!",
            });
        } catch (error) {
            console.error("Error adding song:", error);
            toast({
                title: "Error",
                description: error.response?.data.error || "Failed to add song",
                variant: "destructive",
            });
        } finally {
            setIsAddingSong(false);
        }
    };

    const handleSkip = async () => {
        if (currentSong) {
            try {
                await api.post(
                    `/rooms/${roomId}/songs/${currentSong.id}/ended`
                );
            } catch (error) {
                console.error("Error skipping song:", error);
                toast({
                    title: "Error",
                    description: "Failed to skip song",
                    variant: "destructive",
                });
            }
        }
    };

    const handleLeaveRoom = async () => {
        try {
            cleanupWebSocket();
            navigate("/home");
        } catch (error) {
            console.error("Error leaving room:", error);
            toast({
                title: "Error",
                description: "Failed to leave room. Please try again.",
                variant: "destructive",
            });
            // Still navigate home in case of error
            navigate("/home");
        }
    };

    const handleDeleteRoom = async () => {
        try {
            await api.post(`/rooms/${roomId}/close`);
            toast({
                title: "Success",
                description: "Room deleted successfully!",
            });
            navigate("/home");
        } catch (error) {
            console.error("Error deleting room:", error);
            toast({
                title: "Error",
                description:
                    error.response?.data.error || "Failed to delete room",
                variant: "destructive",
            });
        }
    };

    // Add this useEffect to handle creator left message
    useEffect(() => {
        if (client && client.connected) {
            const statusSubscription = client.subscribe(
                `/topic/room/${roomId}/status`,
                (message) => {
                    const status = message.body;
                    if (status === "CREATOR_LEFT") {
                        toast({
                            title: "Room Closed",
                            description: "The room creator has left. You will be redirected to home.",
                        });
                        navigate("/home");
                    }
                }
            );

            return () => {
                if (statusSubscription) {
                    statusSubscription.unsubscribe();
                }
            };
        }
    }, [client, roomId, navigate, toast]);

    if (loading) {
        return (
            <div className="flex items-center justify-center h-screen bg-slate-950">
                <Loader className="w-12 h-12 text-amber-400 animate-spin" />
                <p className="ml-4 text-xl text-amber-400 animate-pulse">
                    Hang on tight, while we let you in!
                </p>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gradient-to-r from-slate-900 to-slate-800 text-slate-100 p-4">
            <Card className="bg-slate-800/50 border-slate-700 shadow-xl backdrop-blur-sm max-w-6xl mx-auto">
                <RoomHeader
                    roomName={roomName}
                    roomId={roomId}
                    activeUsers={activeUsers}
                    isCreator={isCreator}
                    onLeaveRoom={handleLeaveRoom}
                    onDeleteRoom={handleDeleteRoom}
                />
                <CardContent className="p-4 sm:p-6">
                    <div className="flex flex-col lg:flex-row gap-6">
                        {/* Left side: Tabs with Queue and Playlist */}
                        <div className="w-full lg:w-3/5 order-2 lg:order-1">
                            <Tabs defaultValue="queue" className="w-full">
                                <TabsList className="grid w-full grid-cols-2 mb-4">
                                    <TabsTrigger value="queue">Queue</TabsTrigger>
                                    <TabsTrigger value="playlists">Playlists</TabsTrigger>
                                </TabsList>
                                <TabsContent value="queue">
                                    <SongQueue
                                        queuedSongs={queuedSongs}
                                        isCreator={isCreator}
                                        handleVote={handleVote}
                                        handlePlayNow={handlePlayNow}
                                        handleSongDelete={handleSongDelete}
                                        loadingVoteIds={loadingIds.vote}
                                        loadingPlayNowIds={loadingIds.playNow}
                                        loadingDeleteIds={loadingIds.delete}
                                    />
                                </TabsContent>
                                <TabsContent value="playlists">
                                    <PlaylistTab roomId={roomId} onAddToQueue={handleAddToQueue} />
                                </TabsContent>
                            </Tabs>
                        </div>

                        {/* Right side: Add Song Form and Player */}
                        <div className="w-full lg:w-2/5 order-1 lg:order-2">
                            <AddSongForm
                                youtubeLink={youtubeLink}
                                setYoutubeLink={setYoutubeLink}
                                addSong={addSong}
                                isAddingSong={isAddingSong}
                            />
                            {currentSong && (
                                <div className="bg-slate-700 rounded-lg p-4 shadow-lg mt-4">
                                    <h3 className="text-xl font-semibold text-amber-400 mb-2">Now Playing</h3>
                                    <p className="text-slate-300 mb-4 line-clamp-2">{currentSong.title}</p>
                                    <div className="aspect-w-16 aspect-h-9 mb-4 rounded-md overflow-hidden">
                                        <SyncedPlayer
                                            currentSong={currentSong}
                                            isCreator={isCreator}
                                            client={client}
                                            roomId={roomId}
                                            onSongEnd={onSongEnd}
                                        />
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
