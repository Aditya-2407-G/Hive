import React, { useEffect, useState, useRef } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import YouTube from "react-youtube";
import {
    Card,
    CardHeader,
    CardTitle,
    CardContent,
    CardFooter,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
    Loader,
    Music,
    ThumbsUp,
    Users,
    Share2,
    Loader2,
    Play,
    Pause,
    SkipForward,
    PlayCircle,
    Trash2,
} from "lucide-react";
import { useApi } from "@/hooks/api";
import { useAuth } from "@/context/AuthProvider";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/hooks/use-toast";
import { motion, AnimatePresence } from "framer-motion";

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
    const [loadingVoteIds, setLoadingVoteIds] = useState([]);
    const [loadingPlayNowIds, setLoadingPlayNowIds] = useState([]);
    const [loadingDeleteIds, setLoadingDeleteIds] = useState([]);
    const [isPlaying, setIsPlaying] = useState(true);
    const playerRef = useRef(null);

    const { roomName, shareableLink } = location.state || {};

    useEffect(() => {
        fetchRoomData();
        checkIfCreator();
        setupWebSocket();

        return () => {
            if (client && client.connected) {
                client.publish({
                    destination: `/app/room/${roomId}/leave`,
                    body: JSON.stringify({ email: auth.email }),
                });
            }
            if (client) client.deactivate();
        };
    }, [roomId]);

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
        const socket = new SockJS("http://localhost:8080/ws");
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
        stompClient.subscribe(`/topic/room/${roomId}/songs`, (message) => {
            const updatedSongs = JSON.parse(message.body);
            updateSongsList(updatedSongs);
        });

        stompClient.subscribe(`/topic/room/${roomId}/status`, (message) => {
            if (message.body === "CLOSED" || message.body === "CREATOR_LEFT") {
                handleRoomClosure(message.body);
            }
        });

        stompClient.subscribe(
            `/topic/room/${roomId}/vote-update`,
            (message) => {
                const voteUpdate = JSON.parse(message.body);
                handleVoteUpdate(voteUpdate);
            }
        );

        stompClient.subscribe(`/topic/room/${roomId}/votes`, (message) => {
            const updatedSong = JSON.parse(message.body);
            updateSongVotes(updatedSong);
        });

        stompClient.subscribe(`/topic/room/${roomId}/song-ended`, (message) => {
            const { endedSongId, newSongOrder } = JSON.parse(message.body);
            handleSongEnded(endedSongId, newSongOrder);
        });

        stompClient.subscribe(
            `/topic/room/${roomId}/activeUsers`,
            (message) => {
                setActiveUsers(parseInt(message.body));
            }
        );

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
        // Clear the loading state for the voted song
        setLoadingVoteIds((prev) =>
            prev.filter((id) => !updatedSongs.some((song) => song.id === id))
        );
    };

    const handleVote = async (songId) => {
        try {
          setLoadingVoteIds((prev) => [...prev, songId]);
    
          // Send vote to server
          const response = await api.post(`/rooms/songs/${songId}/vote`);
          const updatedSong = response.data;
    
          // Update the songs state immediately
          setSongs((prevSongs) =>
            prevSongs.map((song) =>
              song.id === updatedSong.id ? { ...song, upvotes: updatedSong.upvotes } : song
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
            description: error.response?.data.error || "Failed to vote on song",
            variant: "destructive",
          });
        } finally {
          setLoadingVoteIds((prev) => prev.filter((id) => id !== songId));
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
            setLoadingPlayNowIds((prev) => [...prev, songId]);
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
            setLoadingPlayNowIds((prev) => prev.filter((id) => id !== songId));
        }
    };

    const handleSongDelete = async (songId) => {
        try {
            setLoadingDeleteIds((prev) => [...prev, songId]);
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
            setLoadingDeleteIds((prev) => prev.filter((id) => id !== songId));
        }
    };

    const onSongEnd = async () => {
        if (currentSong) {
            try {
                await api.post(
                    `/rooms/${roomId}/songs/${currentSong.id}/ended`
                );
            } catch (error) {
                console.error("Error handling song end:", error);
                toast({
                    title: "Error",
                    description: "Failed to update song queue",
                    variant: "destructive",
                });
            }
        }
    };

    const isValidYoutubeLink = (url) => {
        const youtubeRegex =
            /^(https?:\/\/)?(www\.)?(youtube\.com|youtu\.?be)\/.+$/;
        return youtubeRegex.test(url);
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

        if (!isValidYoutubeLink(youtubeLink)) {
            toast({
                title: "Error",
                description: "Please enter a valid YouTube link",
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

    const handlePlayPause = () => {
        if (playerRef.current) {
            if (isPlaying) {
                playerRef.current.internalPlayer.pauseVideo();
            } else {
                playerRef.current.internalPlayer.playVideo();
            }
            setIsPlaying(!isPlaying);
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
            if (client && client.connected) {
                client.publish({
                    destination: `/app/room/${roomId}/leave`,
                    body: JSON.stringify({ email: auth.email }),
                });
            }

            if (!isCreator) {
                navigate("/home");
            }
        } catch (error) {
            console.error("Error leaving room:", error);
            toast({
                title: "Error",
                description: "Failed to leave room. Please try again.",
                variant: "destructive",
            });
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
        <div className="min-h-screen bg-gradient-to-r from-slate-900 to-slate-800 text-slate-100 p-4 md:p-8">
            <Card className="bg-slate-800/50 border-slate-700 shadow-xl backdrop-blur-sm max-w-6xl mx-auto">
                <CardHeader className="border-b border-slate-700">
                    <CardTitle className="text-3xl font-bold text-amber-400 flex flex-col sm:flex-row items-center justify-between">
                        <div className="flex items-center mb-4 sm:mb-0">
                            <Music className="mr-2" />{" "}
                            {roomName || `Room ${roomId}`}
                        </div>
                        <div className="flex items-center space-x-4">
                            <Badge
                                variant="secondary"
                                className="text-sm bg-slate-700 text-amber-400"
                            >
                                <Users className="w-4 h-4 mr-1" />
                                {activeUsers} active
                            </Badge>
                            <Button
                                onClick={handleLeaveRoom}
                                variant="destructive"
                                className="bg-red-600 hover:bg-red-700 text-white transition-colors duration-200"
                            >
                                Leave Room
                            </Button>
                            {isCreator && (
                                <Button
                                    onClick={handleDeleteRoom}
                                    variant="destructive"
                                    className="bg-red-600 hover:bg-red-700 text-white transition-colors duration-200"
                                >
                                    Delete Room
                                </Button>
                            )}
                        </div>
                    </CardTitle>
                </CardHeader>
                <CardContent className="p-6">
                    <div className="flex flex-col lg:flex-row gap-8">
                        <div className="w-full lg:w-3/5 order-2 lg:order-1">
                            <div className="flex flex-col sm:flex-row justify-between mb-4">
                                <h3 className="text-2xl font-semibold text-amber-400 mb-2 sm:mb-0">
                                    Up Next
                                </h3>
                                <Button
                                    onClick={generateShareableLink}
                                    className="w-full sm:w-auto bg-amber-400 hover:bg-amber-500 text-slate-900 font-semibold transition-colors duration-200"
                                >
                                    <Share2 className="mr-2 h-4 w-4" /> Share
                                </Button>
                            </div>
                            <ScrollArea className="h-[500px] rounded-md border border-slate-600 p-4 bg-slate-800/30 backdrop-blur-sm">
                                <AnimatePresence>
                                    {queuedSongs.map((song, index) => (
                                        <motion.div
                                            key={song.id}
                                            initial={{ opacity: 0, y: 20 }}
                                            animate={{ opacity: 1, y: 0 }}
                                            exit={{ opacity: 0, y: -20 }}
                                            transition={{ duration: 0.3 }}
                                            layout
                                        >
                                            {index > 0 && (
                                                <Separator className="my-2 bg-slate-600" />
                                            )}
                                            <div className="flex justify-between items-center py-2">
                                                <div className="flex-1">
                                                    <h4 className="text-lg font-medium text-amber-300">
                                                        {song.title}
                                                    </h4>
                                                    <motion.p
                                                        className="text-sm text-slate-400"
                                                        key={`upvotes-${song.id}-${song.upvotes}`}
                                                        initial={{
                                                            scale: 1.2,
                                                            color: "#fbbf24",
                                                        }}
                                                        animate={{
                                                            scale: 1,
                                                            color: "#94a3b8",
                                                        }}
                                                        transition={{
                                                            duration: 0.3,
                                                        }}
                                                    >
                                                        Upvotes: {song.upvotes}
                                                    </motion.p>
                                                </div>
                                                <div className="flex items-center space-x-2">
                                                    <Button
                                                        onClick={() =>
                                                            handleVote(
                                                                song.id,
                                                                true
                                                            )
                                                        }
                                                        variant="ghost"
                                                        size="sm"
                                                        className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10 transition-colors duration-200"
                                                        disabled={loadingVoteIds.includes(
                                                            song.id
                                                        )}
                                                    >
                                                        {loadingVoteIds.includes(
                                                            song.id
                                                        ) ? (
                                                            <Loader2 className="h-4 w-4 animate-spin" />
                                                        ) : (
                                                            <ThumbsUp className="h-5 w-5" />
                                                        )}
                                                    </Button>
                                                    {isCreator && (
                                                        <>
                                                            <Button
                                                                onClick={() =>
                                                                    handlePlayNow(
                                                                        song.id
                                                                    )
                                                                }
                                                                variant="ghost"
                                                                size="sm"
                                                                className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10 transition-colors duration-200"
                                                                disabled={loadingPlayNowIds.includes(
                                                                    song.id
                                                                )}
                                                            >
                                                                {loadingPlayNowIds.includes(
                                                                    song.id
                                                                ) ? (
                                                                    <Loader2 className="h-4 w-4 animate-spin" />
                                                                ) : (
                                                                    <PlayCircle className="h-5 w-5" />
                                                                )}
                                                            </Button>
                                                            <Button
                                                                onClick={() =>
                                                                    handleSongDelete(
                                                                        song.id
                                                                    )
                                                                }
                                                                variant="ghost"
                                                                size="sm"
                                                                className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10 transition-colors duration-200"
                                                                disabled={loadingDeleteIds.includes(
                                                                    song.id
                                                                )}
                                                            >
                                                                {loadingDeleteIds.includes(
                                                                    song.id
                                                                ) ? (
                                                                    <Loader2 className="h-4 w-4 animate-spin" />
                                                                ) : (
                                                                    <Trash2 className="h-5 w-5" />
                                                                )}
                                                            </Button>
                                                        </>
                                                    )}
                                                </div>
                                            </div>
                                        </motion.div>
                                    ))}
                                </AnimatePresence>
                            </ScrollArea>
                        </div>
                        <div className="w-full lg:w-2/5 order-1 lg:order-2">
                            <h3 className="text-2xl font-semibold text-amber-400 mb-4">
                                Now Playing
                            </h3>
                            {currentSong ? (
                                <motion.div
                                    initial={{ opacity: 0, scale: 0.9 }}
                                    animate={{ opacity: 1, scale: 1 }}
                                    transition={{ duration: 0.5 }}
                                    className="bg-slate-700 rounded-lg p-4 shadow-lg"
                                >
                                    <h4 className="text-xl text-amber-300 mb-3">
                                        {currentSong.title}
                                    </h4>
                                    <div className="aspect-w-16 aspect-h-9 mb-4 rounded-md overflow-hidden">
                                        <YouTube
                                            videoId={
                                                currentSong.youtubeLink.split(
                                                    "v="
                                                )[1]
                                            }
                                            opts={{
                                                height: "100%",
                                                width: "100%",
                                                playerVars: {
                                                    autoplay: 1,
                                                },
                                            }}
                                            onEnd={onSongEnd}
                                            ref={playerRef}
                                        />
                                    </div>
                                    {isCreator && (
                                        <div className="flex justify-center space-x-4">
                                            <Button
                                                onClick={handlePlayPause}
                                                className="bg-amber-400 text-slate-900 hover:bg-amber-500 transition-colors duration-200"
                                            >
                                                {isPlaying ? (
                                                    <Pause className="mr-2 h-4 w-4" />
                                                ) : (
                                                    <Play className="mr-2 h-4 w-4" />
                                                )}
                                                {isPlaying ? "Pause" : "Play"}
                                            </Button>
                                            <Button
                                                onClick={handleSkip}
                                                className="bg-amber-400 text-slate-900 hover:bg-amber-500 transition-colors duration-200"
                                            >
                                                <SkipForward className="mr-2 h-4 w-4" />
                                                Skip
                                            </Button>
                                        </div>
                                    )}
                                </motion.div>
                            ) : (
                                <p className="text-slate-400 italic">
                                    No songs in the queue.
                                </p>
                            )}
                            <Card className="bg-slate-700 border-slate-600 mt-8 shadow-lg">
                                <CardHeader>
                                    <CardTitle className="text-2xl text-amber-400">
                                        Add a Song
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="flex flex-col sm:flex-row space-y-2 sm:space-y-0 sm:space-x-2">
                                    <Input
                                        type="text"
                                        value={youtubeLink}
                                        onChange={(e) =>
                                            setYoutubeLink(e.target.value)
                                        }
                                        placeholder="Enter YouTube link"
                                        className="bg-slate-600 border-slate-500 text-slate-100 placeholder-slate-400 flex-grow"
                                        disabled={isAddingSong}
                                    />
                                    <Button
                                        onClick={addSong}
                                        disabled={isAddingSong}
                                        className="bg-amber-400 text-slate-900 hover:bg-amber-500 transition-colors duration-200 w-full sm:w-auto"
                                    >
                                        {isAddingSong ? (
                                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                        ) : (
                                            <Music className="mr-2 h-4 w-4" />
                                        )}
                                        {isAddingSong
                                            ? "Adding..."
                                            : "Add Song"}
                                    </Button>
                                </CardContent>
                            </Card>
                        </div>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
