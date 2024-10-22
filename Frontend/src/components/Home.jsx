import React, { useEffect, useState } from "react";
import { useAuth } from "../context/AuthProvider";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useNavigate } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import { useToast } from "@/hooks/use-toast";
import {
    Music,
    LogOut,
    Link as LinkIcon,
    Headphones,
    Loader,
    Loader2,
} from "lucide-react";
import { useApi } from "@/hooks/api";

export default function Home() {
    const apiInterceptor = useApi();
    const { auth, logout } = useAuth();
    const [rooms, setRooms] = useState([]);
    const [roomName, setRoomName] = useState("");
    const [shareableLink, setShareableLink] = useState("");
    const [isCreating, setIsCreating] = useState(false);
    const [loading, setLoading] = useState(true);
    const navigate = useNavigate();
    const { toast } = useToast();
    const [isLoggingOut, setIsLoggingOut] = useState(false);
    const [isCreatingRoom, setIsCreatingRoom] = useState(false);
    const [isJoiningRoom, setIsJoiningRoom] = useState(false);

    useEffect(() => {
        if (!auth.isAuthenticated) {
            navigate("/login");
            return;
        }
        fetchUserRooms();
    }, [auth.isAuthenticated, navigate]);

    const handleLogout = async () => {
        setIsLoggingOut(true);
        try {
            await logout();
            toast({
                title: "Logged out successfully",
            });
            navigate("/login");
        } catch (error) {
            console.error("Error logging out:", error);
            toast({
                variant: "destructive",
                title: "Error logging out",
                description: "Please try again later",
            });
        } finally {
            setIsLoggingOut(false);
        }
    }

    const fetchUserRooms = async () => {
        try {
            setLoading(true);
            const response = await apiInterceptor.get('/rooms');
            setRooms(Array.isArray(response.data) ? response.data : []);
        } catch (error) {
            console.error("Error fetching rooms:", error);
            toast({
                variant: "destructive",
                title: "Error fetching rooms",
                description: "Please try again later",
            });
            setRooms([]);
        } finally {
            setLoading(false);
        }
    };

    const createRoom = async () => {
        setIsCreatingRoom(true);
        try {
            await apiInterceptor.post('/rooms', { roomName });
            setRoomName("");
            fetchUserRooms();
            setIsCreating(false);
            toast({
                title: "Success",
                description: "Room created successfully",
            });
        } catch (error) {
            console.error("Error creating room:", error);
            toast({
                variant: "destructive",
                title: "Error creating room",
                description: error.response.data.error || "Please try again later",
            });
        } finally {
            setIsCreatingRoom(false);
        }
    };

    const joinRoom = async () => {
        setIsJoiningRoom(true);
        try {
            await apiInterceptor.post(`/rooms/join/${shareableLink}`);
            setShareableLink("");
            fetchUserRooms();
            toast({
                title: "Success",
                description: "Joined room successfully",
            });
        } catch (error) {
            console.error("Error joining room:", error);
            toast({
                variant: "destructive",
                title: "Error ",
                description: "Please check the link and try again",
            });
        } finally {
            setIsJoiningRoom(false);
        }
    };

    const viewSongs = (room) => {
        navigate(`/rooms/${room.id}`, {
            state: {
                roomName: room.name,
                shareableLink: room.shareableLink,
            },
        });
    };

    const handleCreateRoom = () => {
        if (!roomName.trim()) {
            toast({
                variant: "destructive",
                title: "Error",
                description: "Room name cannot be empty!",
            });
            return;
        }
        createRoom();
    };

    const handleJoinRoom = () => {
        if (!shareableLink.trim()) {
            toast({
                variant: "destructive",
                title: "Error",
                description: "Shareable link cannot be empty!",
            });
            return;
        }
        joinRoom();
    };

    return (
        <div className="min-h-screen bg-slate-950 text-slate-100 p-8">
            <motion.div
                initial={{ opacity: 0, y: -20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5 }}
                className="max-w-6xl mx-auto"
            >
                <header className="flex justify-between items-center mb-12">
                    <h1 className="text-4xl font-bold text-transparent bg-clip-text bg-amber-400">
                        Welcome to Hive, {auth.user}!
                    </h1>
                    <Button
                        variant="ghost"
                        onClick={handleLogout}
                        disabled={isLoggingOut}
                        className="text-slate-300 hover:text-white hover:bg-slate-800"
                    >
                        {isLoggingOut ? (
                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        ) : (
                            <LogOut className="mr-2 h-4 w-4" />
                        )}
                        {isLoggingOut ? "Logging out..." : "Logout"}
                    </Button>
                </header>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                    <Card className="bg-slate-900 border-slate-800 col-span-1 md:col-span-2 shadow-xl">
                        <CardHeader>
                            <CardTitle className="text-2xl font-bold text-amber-400 flex items-center">
                                <Music className="mr-2" /> Your Music Rooms
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            {loading ? (
                                <div className="flex justify-center py-8">
                                    <Loader className="animate-spin h-8 w-8 text-[#FCD34D]" />
                                    <span className="ml-2 text-slate-300">
                                        Loading rooms...
                                    </span>
                                </div>
                            ) : rooms.length === 0 ? (
                                <div className="text-center py-8">
                                    <p className="text-slate-300">
                                        No rooms created yet.
                                    </p>
                                </div>
                            ) : (
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                    {rooms.map((room) => (
                                        <motion.div
                                            key={room.id}
                                            whileHover={{ scale: 1.03 }}
                                            whileTap={{ scale: 0.98 }}
                                        >
                                            <Card
                                                className="bg-slate-800 border-slate-700 cursor-pointer transition-all duration-300 hover:shadow-lg hover:shadow-[#FCD34D]/20"
                                                onClick={() => viewSongs(room)}
                                            >
                                                <CardContent className="p-6">
                                                    <h3 className="text-xl font-semibold mb-2 text-[#FCD34D]">
                                                        {room.name}
                                                    </h3>
                                                    <p className="text-sm text-slate-300 mb-4">
                                                        Room ID: {room.id}
                                                    </p>
                                                    <div className="flex items-center text-slate-400">
                                                        <LinkIcon className="h-4 w-4 mr-2" />
                                                        <span className="text-xs truncate">
                                                            {room.shareableLink}
                                                        </span>
                                                    </div>
                                                </CardContent>
                                            </Card>
                                        </motion.div>
                                    ))}
                                </div>
                            )}
                        </CardContent>
                    </Card>

                    <Card className="bg-slate-900 border-slate-800 shadow-xl">
                        <CardHeader>
                            <CardTitle className="text-2xl font-bold text-amber-400 flex items-center">
                                <Headphones className="mr-2" /> Join the Beat
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-6">
                            <AnimatePresence mode="wait">
                                {isCreating ? (
                                    <motion.div
                                        key="create"
                                        initial={{ opacity: 0, y: 20 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        exit={{ opacity: 0, y: -20 }}
                                        transition={{ duration: 0.3 }}
                                    >
                                        <Input
                                            type="text"
                                            value={roomName}
                                            onChange={(e) =>
                                                setRoomName(e.target.value)
                                            }
                                            placeholder="Enter room name"
                                            className="bg-slate-800 border-slate-700 text-slate-100 placeholder-slate-400 mb-2"
                                        />
                                        <Button
                                            onClick={handleCreateRoom}
                                            disabled={isCreatingRoom}
                                            className="w-full bg-amber-400 text-slate-900 hover:bg-amber-500 transition-colors duration-300"
                                        >
                                            {isCreatingRoom ? (
                                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                            ) : (
                                                <Music className="mr-2 h-4 w-4" />
                                            )}
                                            {isCreatingRoom
                                                ? "Creating..."
                                                : "Create Room"}
                                        </Button>
                                    </motion.div>
                                ) : (
                                    <motion.div
                                        key="join"
                                        initial={{ opacity: 0, y: 20 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        exit={{ opacity: 0, y: -20 }}
                                        transition={{ duration: 0.3 }}
                                    >
                                        <Input
                                            type="text"
                                            value={shareableLink}
                                            onChange={(e) =>
                                                setShareableLink(e.target.value)
                                            }
                                            placeholder="Enter shareable link"
                                            className="bg-slate-800 border-slate-700 text-slate-100 placeholder-slate-400 mb-2"
                                        />
                                        <Button
                                            onClick={handleJoinRoom}
                                            disabled={isJoiningRoom}
                                            className="w-full bg-[#FCD34D] text-slate-900 hover:bg-amber-400 transition-colors duration-300"
                                        >
                                            {isJoiningRoom ? (
                                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                            ) : (
                                                <Headphones className="mr-2 h-4 w-4" />
                                            )}
                                            {isJoiningRoom
                                                ? "Joining..."
                                                : "Join Room"}
                                        </Button>
                                    </motion.div>
                                )}
                            </AnimatePresence>
                            <Button
                                onClick={() => setIsCreating(!isCreating)}
                                className="w-full bg-slate-800 text-amber-400 hover:bg-slate-700 border border-amber-400 transition-colors duration-300"
                            >
                                {isCreating
                                    ? "Join Existing Room"
                                    : "Create New Room"}
                            </Button>
                        </CardContent>
                    </Card>
                </div>
            </motion.div>
        </div>
    );
}