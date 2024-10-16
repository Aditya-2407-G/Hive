import React, { useEffect, useState, useCallback, useRef } from "react";
import { useParams, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthProvider";
import axios from "axios";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import {
    ThumbsUp,
    Play,
    Pause,
    Link as LinkIcon,
    Music,
    X,
    Loader2,
} from "lucide-react";
import YouTube from "react-youtube";
import { motion, AnimatePresence } from "framer-motion";
import { useToast } from "@/hooks/use-toast";
import { useApi } from "@/hooks/api";

const API_BASE_URL = "http://localhost:8080/api";

export default function RoomSongs() {
    const apiInterceptor = useApi();
    const { roomId } = useParams();
    const location = useLocation();
    const { auth } = useAuth();
    const { toast } = useToast();
    const [songs, setSongs] = useState([]);
    const [youtubeLink, setYoutubeLink] = useState("");
    const [currentSong, setCurrentSong] = useState(null);
    const [isPlaying, setIsPlaying] = useState(false);
    const [roomName, setRoomName] = useState(location.state?.roomName || "");
    const [shareableLink, setShareableLink] = useState(
        location.state?.shareableLink || ""
    );
    const [isAddingSong, setIsAddingSong] = useState(false);
    const [isVoting, setIsVoting] = useState(false);
    const [isClosingRoom, setIsClosingRoom] = useState(false);
    const navigate = useNavigate();
    const [isLoadingSongs, setIsLoadingSongs] = useState(true);
    const [isTogglingPlayback, setIsTogglingPlayback] = useState(false);
    const [loadingSongId, setLoadingSongId] = useState(null);
    const playerRef = useRef(null);


    useEffect(() => {
        if (!auth.isAuthenticated) {
            navigate("/login");
            return;
        }
    }, [auth.isAuthenticated, navigate]);

    const fetchSongs = useCallback(async () => {
        setIsLoadingSongs(true);
        try {
            const response = await apiInterceptor.get(
                `/rooms/${roomId}/songs`,
                {
                    headers: { Authorization: `Bearer ${auth.accessToken}` },
                }
            );
            const sortedSongs = response.data.sort((a, b) => b.votes - a.votes);
            setSongs(sortedSongs);
            if (sortedSongs.length > 0 && !currentSong) {
                setCurrentSong(sortedSongs[0]);
                toast({
                    title: "New Song Playing",
                    description: `Now playing: ${sortedSongs[0].title}`,
                });
            }
        } catch (error) {
            console.error("Error fetching songs:", error);
            if (error.response?.status === 404) {
                toast({
                    title: "Error",
                    description: "Room not found. It may have been closed or deleted.",
                    variant: "destructive",
                });
            } else {
                toast({
                    title: "Error",
                    description: "Failed to fetch songs. Please try again.",
                    variant: "destructive",
                });
            }
        } finally {
            setIsLoadingSongs(false);
        }
    }, [roomId, auth.accessToken, currentSong, toast]);

    useEffect(() => {
        fetchSongs();
        const interval = setInterval(fetchSongs, 20000);
        return () => clearInterval(interval);
    }, [fetchSongs]);

    const addSong = async () => {
        if (!youtubeLink) {
            toast({
                title: "Error",
                description: "Please enter a valid YouTube link",
                variant: "destructive",
            });
            return;
        }
        setIsAddingSong(true);
        try {
            await apiInterceptor.post(
                `/rooms/${roomId}/songs`,
                { youtubeLink },
                {
                    headers: { Authorization: `Bearer ${auth.accessToken}` },
                }
            );
            setYoutubeLink("");
            fetchSongs();
            toast({
                title: "Success",
                description: "Song added successfully!",
            });
        } catch (error) {
            console.error("Error adding song:", error);
            toast({
                title: "Error",
                description:
                    error.response?.data?.message ||
                    "Failed to add song. Please try again.",
                variant: "destructive",
            });
        } finally {
            setIsAddingSong(false);
        }
    };

    const voteSong = async (songId) => {
        setLoadingSongId(songId);
        setIsVoting(true);
        try {
            await apiInterceptor.post(
                `/rooms/songs/${songId}/vote`,
                {},
                {
                    headers: { Authorization: `Bearer ${auth.accessToken}` },
                    params: { isUpvote: true },
                }
            );
            fetchSongs();
            toast({
                title: "Success",
                description: "Vote recorded successfully!",
            });
        } catch (error) {
            console.error("Error voting on song:", error);
            toast({
                title: "Error",
                description:
                    "Failed to vote. You may have already voted for this song.",
                variant: "destructive",
            });
        } finally {
            setIsVoting(false);
            setLoadingSongId(null);
        }
    };

    const togglePlayPause = async () => {
        setIsTogglingPlayback(true);
        try {
            setIsPlaying(!isPlaying);
            if (playerRef.current) {
                if (isPlaying) {
                    await playerRef.current.pauseVideo();
                    toast({
                        title: "Paused",
                        description: "Music playback paused",
                    });
                } else {
                    await playerRef.current.playVideo();
                    toast({
                        title: "Playing",
                        description: "Music playback resumed",
                    });
                }
            }
        } catch (error) {
            console.error("Error toggling playback:", error);
            toast({
                title: "Error",
                description: "Failed to toggle playback. Please try again.",
                variant: "destructive",
            });
        } finally {
            setIsTogglingPlayback(false);
        }
    };

    const onSongEnd = () => {
        const nextSong = songs
            .filter((song) => song.id !== currentSong.id)
            .sort((a, b) => b.upvotes - a.upvotes)[0];
        if (nextSong) {
            setCurrentSong(nextSong);
            setIsPlaying(true);
            toast({
                title: "Next Song",
                description: `Now playing: ${nextSong.title}`,
            });
        } else {
            setIsPlaying(false);
            toast({
                title: "Playlist End",
                description: "No more songs in the queue",
            });
        }
    };

    const copyLinkToClipboard = () => {
        navigator.clipboard.writeText(shareableLink);
        toast({
            title: "Link Copied",
            description: "Room link copied to clipboard!",
        });
    };

    const closeRoom = async () => {
        setIsClosingRoom(true);
        try {
            await apiInterceptor.delete(`/rooms/${roomId}/close`, {
                headers: { Authorization: `Bearer ${auth.accessToken}` },
            });
            toast({
                title: "Room Closed",
                description: "The room has been closed successfully.",
            });
        } catch (error) {
            console.error("Error closing room:", error);
            toast({
                title: "Error",
                description: "Failed to close the room. Please try again.",
                variant: "destructive",
            });
        } finally {
            setIsClosingRoom(false);
        }
    };

    return (
        <div className="min-h-screen bg-[#020617] text-slate-100 p-8">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <h1 className="text-4xl font-bold text-amber-400">
                        {roomName || (
                            <div className="flex items-center">
                                <Loader2 className="h-6 w-6 animate-spin mr-2" />
                                Loading...
                            </div>
                        )}
                    </h1>
                    <div className="flex items-center space-x-4">
                        <motion.div
                            whileHover={{ scale: 1.05 }}
                            whileTap={{ scale: 0.95 }}
                        >
                            <Button
                                onClick={copyLinkToClipboard}
                                className="bg-gradient-to-r from-blue-500 to-teal-500 text-white hover:from-blue-600 hover:to-teal-600"
                            >
                                <LinkIcon className="mr-2" /> Share
                            </Button>
                        </motion.div>
                        <motion.div
                            whileHover={{ scale: 1.05 }}
                            whileTap={{ scale: 0.95 }}
                        >
                            <Button
                                onClick={closeRoom}
                                disabled={isClosingRoom}
                                className="bg-red-500 text-white hover:bg-red-600"
                            >
                                {isClosingRoom ? (
                                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                ) : (
                                    <X className="mr-2 h-4 w-4" />
                                )}
                                {isClosingRoom ? "Closing..." : "Close Room"}
                            </Button>
                        </motion.div>
                    </div>
                </div>

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
                            className="bg-amber-400 text-slate-900 hover:bg-amber-300"
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

                <div className="flex flex-col lg:flex-row lg:space-x-6">
                    <div className="w-full lg:w-3/5 mb-8 lg:mb-0">
                        <h2 className="text-2xl font-bold text-amber-400 mb-4">
                            Up Next
                        </h2>
                        {isLoadingSongs ? (
                            <div className="flex items-center justify-center p-8">
                                <Loader2 className="h-8 w-8 animate-spin" />
                            </div>
                        ) : (
                            <AnimatePresence>
                                {songs
                                    .filter(
                                        (song) => song.id !== currentSong?.id
                                    )
                                    .map((song) => (
                                        <motion.div
                                            key={song.id}
                                            initial={{ opacity: 0, y: 20 }}
                                            animate={{ opacity: 1, y: 0 }}
                                            exit={{ opacity: 0, y: -20 }}
                                            transition={{ duration: 0.3 }}
                                            className="mb-4"
                                        >
                                            <Card className="bg-slate-800 border-slate-700 hover:bg-slate-750 transition-colors duration-200">
                                                <CardContent className="flex justify-between items-center py-4">
                                                    <p className="text-lg text-white truncate flex-grow mr-4">
                                                        {song.title}
                                                    </p>
                                                    <Button
                                                        onClick={() =>
                                                            voteSong(song.id)
                                                        }
                                                        disabled={
                                                            isVoting ||
                                                            loadingSongId ===
                                                                song.id
                                                        }
                                                        className="bg-green-500 hover:bg-green-600 transition-colors duration-200"
                                                        aria-label="Upvote"
                                                    >
                                                        {loadingSongId ===
                                                        song.id ? (
                                                            <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                                                        ) : (
                                                            <ThumbsUp className="h-4 w-4 mr-2" />
                                                        )}
                                                        <span>
                                                            {song.upvotes}
                                                        </span>
                                                    </Button>
                                                </CardContent>
                                            </Card>
                                        </motion.div>
                                    ))}
                            </AnimatePresence>
                        )}
                    </div>

                    <div className="w-full lg:w-2/5">
                        {currentSong && (
                            <Card className="bg-slate-800 border-slate-700 shadow-lg">
                                <CardHeader>
                                    <CardTitle className="text-2xl text-amber-400">
                                        Now Playing
                                    </CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <motion.div
                                        className="w-full aspect-w-16 aspect-h-9 mb-6 overflow-hidden rounded-md shadow-xl"
                                        initial={{ scale: 0.9, opacity: 0 }}
                                        animate={{ scale: 1, opacity: 1 }}
                                        transition={{ duration: 0.5 }}
                                    >
                                        <YouTube
                                            videoId={
                                                currentSong.youtubeLink.split(
                                                    "v="
                                                )[1]
                                            }
                                            opts={{
                                                width: "100%",
                                                height: "220vh",
                                                playerVars: {
                                                    autoplay: isPlaying ? 1 : 0,
                                                    rel: 0,
                                                    modestbranding: 1,
                                                },
                                            }}
                                            onEnd={onSongEnd}
                                            onReady={(event) => {
                                                playerRef.current =
                                                    event.target;
                                            }}
                                        />
                                    </motion.div>
                                    <div className="flex justify-between items-center">
                                        <p className="text-lg font-semibold text-white truncate mr-4">
                                            {currentSong.title}
                                        </p>
                                        <Button
                                            onClick={togglePlayPause}
                                            disabled={isTogglingPlayback}
                                            className="bg-amber-400 text-slate-900 hover:bg-amber-300 transition-colors duration-200"
                                        >
                                            {isTogglingPlayback ? (
                                                <Loader2 className="h-5 w-5 animate-spin" />
                                            ) : isPlaying ? (
                                                <Pause className="h-5 w-5" />
                                            ) : (
                                                <Play className="h-5 w-5" />
                                            )}
                                        </Button>
                                    </div>
                                </CardContent>
                            </Card>
                        )}
                        {!currentSong && !isLoadingSongs && (
                            <Card className="bg-slate-800 border-slate-700 shadow-lg">
                                <CardContent className="flex flex-col items-center justify-center p-8">
                                    <Music className="h-12 w-12 text-amber-400 mb-4" />
                                    <p className="text-lg text-slate-300 text-center">
                                        No songs in the queue. Add a song to get
                                        started!
                                    </p>
                                </CardContent>
                            </Card>
                        )}
                        {!currentSong && isLoadingSongs && (
                            <Card className="bg-slate-800 border-slate-700 shadow-lg">
                                <CardContent className="flex flex-col items-center justify-center p-8">
                                    <Loader2 className="h-12 w-12 text-amber-400 animate-spin mb-4" />
                                    <p className="text-lg text-slate-300">
                                        Loading player...
                                    </p>
                                </CardContent>
                            </Card>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
