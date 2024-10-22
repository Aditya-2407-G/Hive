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
    Trash2
} from "lucide-react";
import { useApi } from "@/hooks/api";
import { useAuth } from "@/context/AuthProvider";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/hooks/use-toast";   

const RoomSongs = () => {
    const location = useLocation();
    const { roomId } = useParams();
    const navigate = useNavigate();
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
    const [loadingSongIds, setLoadingSongIds] = useState([]);
    const [isPlaying, setIsPlaying] = useState(true);
    const playerRef = useRef(null);

    const { roomName, shareableLink } = location.state || {};

    useEffect(() => {
        fetchRoomData();
        checkIfCreator();
    }, [roomId]);

    const updateSongsList = (songsList) => {
        const current = songsList.find(song => song.current);
        const queued = songsList
            .filter(song => !song.current)
            .sort((a, b) => b.upvotes - a.upvotes);
        
        setCurrentSong(current || null);
        setQueuedSongs(queued);
        setSongs(songsList);
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

    useEffect(() => {
        const socket = new SockJS("http://localhost:8080/ws");
        const stompClient = new Client({
            webSocketFactory: () => socket,
            reconnectDelay: 5000,
            debug: (str) => console.log(str),
        });

        stompClient.onConnect = () => {
            console.log("Connected to WebSocket");

            stompClient.subscribe(`/topic/room/${roomId}/songs`, (message) => {
                const updatedSongs = JSON.parse(message.body);
                updateSongsList(updatedSongs);
            });

            stompClient.subscribe(`/topic/room/${roomId}/status`, (message) => {
                if (message.body === "CLOSED") {
                    navigate("/rooms");
                }
            });

            stompClient.subscribe(`/topic/room/${roomId}/votes`, (message) => {
                const updatedSong = JSON.parse(message.body);
                setSongs(prevSongs =>
                    prevSongs
                        .map(song => song.id === updatedSong.id ? updatedSong : song)
                        .sort((a, b) => b.upvotes - a.upvotes)
                );
            });

            stompClient.subscribe(`/topic/room/${roomId}/song-ended`, (message) => {
                const { endedSongId, newSongOrder } = JSON.parse(message.body);
                setSongs(prevSongs => {
                    const updatedSongs = newSongOrder.map(id => {
                        const song = prevSongs.find(s => s.id === id);
                        if (!song) return null;
                        return {
                            ...song,
                            upvotes: song.id === endedSongId ? 0 : song.upvotes,
                            current: id === newSongOrder[0]
                        };
                    }).filter(Boolean);
                    updateSongsList(updatedSongs);
                    return updatedSongs;
                });
            });

            stompClient.subscribe(`/topic/room/${roomId}/activeUsers`, (message) => {
                setActiveUsers(parseInt(message.body));
            });

            stompClient.publish({
                destination: `/app/room/${roomId}/join`,
                body: JSON.stringify({}),
            });
        };

        stompClient.activate();
        setClient(stompClient);

        return () => {
            if (stompClient.connected) {
                stompClient.publish({
                    destination: `/app/room/${roomId}/leave`,
                    body: JSON.stringify({}),
                });
            }
            stompClient.deactivate();
        };
    }, [roomId, navigate]);

    const handleVote = async (songId, isUpvote) => {
        try {
            setLoadingSongIds(prev => [...prev, songId]);
            await api.post(`/rooms/songs/${songId}/vote?isUpvote=${isUpvote}`);
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
            setLoadingSongIds(prev => prev.filter(id => id !== songId));
        }
    };

    const generateShareableLink = async () => {
        try {
            const linkToShare = shareableLink || `${window.location.origin}/join/${roomId}`;
            await navigator.clipboard.writeText(linkToShare);
            toast({
                title: "Link Copied!",
                description: "Shareable link has been copied to your clipboard.",
            });
        } catch (error) {
            console.error("Error generating shareable link:", error);
            toast({
                title: "Error",
                description: "Failed to generate shareable link. Please try again.",
                variant: "destructive",
            });
        }
    };

    const youtubeOpts = {
        height: "250",
        width: "100%",
        playerVars: {
            autoplay: 1,
        },
    };

    const handlePlayNow = async (songId) => {
        if (loadingSongIds.includes(songId)) return;
        
        try {
            setLoadingSongIds(prev => [...prev, songId]);
            await api.post(`/rooms/${roomId}/songs/${songId}/play-now`);
        } catch (error) {
            console.error("Error playing song now:", error);
            toast({
                title: "Error",
                description: error.response?.data.error || "Failed to play song",
                variant: "destructive",
            });
        } finally {
            setLoadingSongIds(prev => prev.filter(id => id !== songId));
        }
    };

    const handleSongDelete = async (songId) => {
        try {
            await api.delete(`/rooms/${roomId}/songs/${songId}/remove`);
            toast({
                title: "Success",
                description: "Song removed successfully!",
            });
        } catch (error) {
            console.error("Error deleting song:", error);
            toast({
                title: "Error",
                description: error.response?.data.error || "Failed to delete song",
                variant: "destructive",
            });
        }
    }

    const onSongEnd = async () => {
        if (currentSong) {
            try {
                await api.post(`/rooms/${roomId}/songs/${currentSong.id}/ended`);
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
        const youtubeRegex = /^(https?:\/\/)?(www\.)?(youtube\.com|youtu\.?be)\/.+$/;
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
            await api.post(`/rooms/${roomId}/songs`, 
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
                await api.post(`/rooms/${roomId}/songs/${currentSong.id}/ended`);
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

    if (loading) {
        return (
            <div className="flex items-center justify-center h-screen bg-slate-950">
                <Loader className="w-8 h-8 text-amber-400 animate-spin" />
                <p className="ml-2 text-amber-400">Hang tight, we're getting you into the room</p>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-slate-950 text-slate-100 p-4 md:p-8">
            <Card className="bg-slate-900 border-slate-800 shadow-xl max-w-6xl mx-auto">
                <CardHeader className="border-b border-slate-800">
                    <CardTitle className="text-3xl font-bold text-amber-400 flex items-center justify-between">
                        <div className="flex items-center">
                            <Music className="mr-2" /> {roomName || `Room ${roomId}`}
                        </div>
                        <Badge variant="secondary" className="text-sm bg-slate-800 text-amber-400">
                            <Users className="w-4 h-4 mr-1" />
                            {activeUsers} active
                        </Badge>
                    </CardTitle>
                </CardHeader>
                <CardContent className="p-6">
                    <div className="flex flex-col md:flex-row gap-8">
                        <div className="w-full md:w-3/5 order-2 md:order-1">
                            <div className="flex flex-row justify-between mb-4">
                                <h3 className="text-2xl font-semibold text-amber-400">
                                    Up Next
                                </h3>
                                <Button
                                    onClick={generateShareableLink}
                                    className="w-50 bg-amber-400 hover:bg-amber-500 text-slate-900 font-semibold"
                                >
                                    <Share2 className="mr-2 h-4 w-4" /> Share 
                                </Button>
                            </div>
                            <ScrollArea className="h-[500px] rounded-md border border-slate-700 p-4">
                {songs.slice(1).map((song, index) => (
                    <React.Fragment key={song.id}>
                        {index > 0 && (
                            <Separator className="my-2 bg-slate-700" />
                        )}
                        <div className="flex justify-between items-center py-2">
                            <div className="flex-1">
                                <h4 className="text-lg font-medium text-amber-300">
                                    {song.title}
                                </h4>
                                <p className="text-sm text-slate-400">
                                    Upvotes: {song.upvotes}
                                </p>
                            </div>
                            <div className="flex items-center space-x-2">
                                <Button
                                    onClick={() => handleVote(song.id, true)}
                                    variant="ghost"
                                    size="sm"
                                    className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10"
                                >
                                    {loadingSongIds.includes(song.id) ? (
                                        <Loader2 className="h-4 w-4 animate-spin" />
                                    ) : (
                                        <ThumbsUp className="h-5 w-5" />
                                    )}
                                </Button>
                                {isCreator && (

                                    <>
                                    <Button
                                        onClick={() => handlePlayNow(song.id)}
                                        variant="ghost"
                                        size="sm"
                                        className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10"
                                        disabled={loadingSongIds.includes(song.id)}
                                        >
                                        {loadingSongIds.includes(song.id) ? (
                                            <Loader2 className="h-4 w-4 animate-spin" />
                                        ) : (
                                            <PlayCircle className="h-5 w-5" />
                                        )}
                                    </Button>

                                    <Button
                                        onClick={() => handleSongDelete(song.id)}
                                        variant="ghost"
                                        size="sm"
                                        className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10"
                                        disabled={loadingSongIds.includes(song.id)}
                                    >
                                        {loadingSongIds.includes(song.id) ? (
                                            <Loader2 className="h-4 w-4 animate-spin" />
                                        ) : (   
                                            <Trash2 className="h-5 w-5" />
                                        )}

                                    </Button>
                                    
                                        </>
                                )}
                            </div>
                        </div>
                    </React.Fragment>
                ))}
            </ScrollArea>

                        </div>
                        <div className="w-full md:w-2/5  order-1 md:order-2">
                            <Card className="bg-slate-800 border-slate-700 mb-8 shadow-lg">
                                <CardHeader>
                                    <CardTitle className="text-2xl text-amber-400">
                                        Add a Song
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="flex space-x-2">
                                    <Input
                                        type="text"
                                        value={youtubeLink}
                                        onChange={(e) => setYoutubeLink(e.target.value)}
                                        placeholder="Enter YouTube link"
                                        className="bg-slate-700 border-slate-600 text-slate-100 placeholder-slate-400"
                                        disabled={isAddingSong}
                                    />
                                    <Button
                                        onClick={addSong}
                                        disabled={isAddingSong}
                                        className="bg-amber-400 text-slate-900 hover:bg-amber-500"
                                    >
                                        {isAddingSong ? (
                                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                        ) : (
                                            <Music className="mr-2 h-4 w-4" />
                                        )}
                                        {isAddingSong ? "Adding..." : "Add Song"}
                                    </Button>
                                </CardContent>
                            </Card>
                            <h3 className="text-2xl font-semibold text-amber-400 mb-4">
                                Now Playing
                            </h3>
                            {songs.length > 0 ? (
                                <div className="bg-slate-800 rounded-lg p-4">
                                    <h4 className="text-xl text-amber-300 mb-3">
                                        {songs[0].title}
                                    </h4>
                                    <div className="aspect-w-16 aspect-h-9 mb-4">
                                        <YouTube
                                            videoId={songs[0].youtubeLink.split("v=")[1]}
                                            opts={youtubeOpts}
                                            onEnd={onSongEnd}
                                            ref={playerRef}
                                        />
                                    </div>
                                    {isCreator && (
                                        <div className="flex justify-center space-x-4">
                                            <Button
                                                onClick={handlePlayPause}
                                                className="bg-amber-400 text-slate-900 hover:bg-amber-500"
                                            >
                                                {isPlaying ? <Pause className="mr-2 h-4 w-4" /> : <Play className="mr-2 h-4 w-4" />}
                                                {isPlaying ? "Pause" : "Play"}
                                            </Button>
                                            <Button
                                                onClick={handleSkip}
                                                className="bg-amber-400 text-slate-900 hover:bg-amber-500"
                                            >
                                                <SkipForward className="mr-2 h-4 w-4" />
                                                Skip
                                            </Button>
                                        </div>
                                    )}
                                </div>
                            ) : (
                                <p className="text-slate-400 italic">
                                    No songs in the queue.
                                </p>
                            )}
                        </div>
                    </div>
                </CardContent>
                <CardFooter className="border-t border-slate-800 p-4">
                </CardFooter>
            </Card>
        </div>
    );
}

export default RoomSongs;