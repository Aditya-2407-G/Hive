import React, { useState, useEffect, useCallback, useRef } from "react";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Plus, Music, Loader2 } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { useApi } from "@/hooks/api";
import AddSongForm from '../AddSongForm';
import PlaylistList from './PlaylistList';
import PlaylistDetails from './PlaylistDetails';
import CreatePlaylistForm from './CreatePlaylistForm';

function PlaylistTab({ roomId, onAddToQueue }) {
    const [playlists, setPlaylists] = useState([]);
    const [selectedPlaylist, setSelectedPlaylist] = useState(null);
    const [loading, setLoading] = useState({
        playlists: false,
        createPlaylist: false,
        deletePlaylist: false,
        addToQueue: {},
    });
    const { toast } = useToast();
    const api = useApi();
    
    const apiRef = useRef(api);
    const toastRef = useRef(toast);
    const pollingIntervalRef = useRef(null);

    useEffect(() => {
        apiRef.current = api;
        toastRef.current = toast;
    }, [api, toast]);

    const fetchPlaylists = useCallback(async () => {
        try {
            setLoading(prev => ({ ...prev, playlists: true }));
            const response = await apiRef.current.get("/playlists");
            setPlaylists(response.data);
            
            if (selectedPlaylist) {
                const updatedSelectedPlaylist = response.data.find(
                    p => p.id === selectedPlaylist.id
                );
                setSelectedPlaylist(updatedSelectedPlaylist || null);
            }
        } catch (error) {
            console.error("Error fetching playlists:", error);
            toastRef.current({
                title: "Error",
                description: "Failed to fetch playlists",
                variant: "destructive",
            });
        } finally {
            setLoading(prev => ({ ...prev, playlists: false }));
        }
    }, [selectedPlaylist]);

    useEffect(() => {
        fetchPlaylists();
        pollingIntervalRef.current = setInterval(fetchPlaylists, 30000);
        return () => clearInterval(pollingIntervalRef.current);
    }, []);

    const handleAddToQueue = async (song) => {
        try {
            setLoading(prev => ({ 
                ...prev, 
                addToQueue: { ...prev.addToQueue, [song.id]: true } 
            }));
            await onAddToQueue(song);
        } finally {
            setLoading(prev => ({ 
                ...prev, 
                addToQueue: { ...prev.addToQueue, [song.id]: false } 
            }));
        }
    };

    return (
        <Card className="w-full max-w-3xl mx-auto bg-slate-800 text-slate-100">
            <CardHeader>
                <CardTitle className="text-2xl font-bold text-amber-400">
                    Playlists
                </CardTitle>
            </CardHeader>
            <CardContent>
                <Tabs defaultValue="playlists" className="w-full">
                    <TabsList className="grid w-full grid-cols-2">
                        <TabsTrigger value="playlists">Playlists</TabsTrigger>
                        <TabsTrigger value="create">Create Playlist</TabsTrigger>
                    </TabsList>
                    
                    <TabsContent value="playlists">
                        <PlaylistList 
                            playlists={playlists}
                            loading={loading.playlists}
                            onSelectPlaylist={setSelectedPlaylist}
                        />
                        
                        {selectedPlaylist && (
                            <PlaylistDetails
                                playlist={selectedPlaylist}
                                loading={loading}
                                onAddToQueue={handleAddToQueue}
                                onDelete={async () => {
                                    try {
                                        setLoading(prev => ({ ...prev, deletePlaylist: true }));
                                        await api.delete(`/playlists/${selectedPlaylist.id}`);
                                        setPlaylists(playlists.filter(p => p.id !== selectedPlaylist.id));
                                        setSelectedPlaylist(null);
                                        toast({ title: "Success", description: "Playlist deleted successfully" });
                                    } catch (error) {
                                        console.error("Error deleting playlist:", error);
                                        toast({
                                            title: "Error",
                                            description: error.response?.data?.error || "Failed to delete playlist",
                                            variant: "destructive",
                                        });
                                    } finally {
                                        setLoading(prev => ({ ...prev, deletePlaylist: false }));
                                    }
                                }}
                            />
                        )}
                    </TabsContent>
                    
                    <TabsContent value="create">
                        <CreatePlaylistForm
                            onSubmit={async (playlistData) => {
                                try {
                                    setLoading(prev => ({ ...prev, createPlaylist: true }));
                                    const response = await api.post("/playlists", playlistData);
                                    setPlaylists([...playlists, response.data]);
                                    toast({ title: "Success", description: "Playlist created successfully" });
                                    return true;
                                } catch (error) {
                                    console.error("Error creating playlist:", error);
                                    toast({
                                        title: "Error",
                                        description: "Failed to create playlist",
                                        variant: "destructive",
                                    });
                                    return false;
                                } finally {
                                    setLoading(prev => ({ ...prev, createPlaylist: false }));
                                }
                            }}
                            isLoading={loading.createPlaylist}
                        />
                    </TabsContent>
                </Tabs>
            </CardContent>
        </Card>
    );
}

export default PlaylistTab;