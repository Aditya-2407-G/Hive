import { Loader2, Plus } from "lucide-react";
import { Button } from "../ui/button";
import { ScrollArea } from "../ui/scroll-area";
import AddSongForm from "../AddSongForm";
import { useState } from "react";
import { useApi } from "@/hooks/api";
import { useToast } from "@/hooks/use-toast";


export default function PlaylistDetails({ playlist, loading, onAddToQueue, onDelete }) {
    const [youtubeLink, setYoutubeLink] = useState("");
    const api = useApi();
    const { toast } = useToast();

    const handleAddSong = async () => {
        try {
            const response = await api.post(
                `/playlists/${playlist.id}/songs`,
                { youtubeLink }
            );
            playlist.songs.push(response.data);
            setYoutubeLink("");
            toast({
                title: "Success",
                description: "Song added to playlist successfully",
            });
        } catch (error) {
            console.error("Error adding song:", error);
            toast({
                title: "Error",
                description: "Failed to add song",
                variant: "destructive",
            });
        }
    };

    return (
        <div className="mt-4">
            <div className="flex justify-between items-center mb-2">
                <h3 className="text-lg font-semibold text-amber-400">
                    {playlist.name}
                </h3>
                <Button
                    onClick={onDelete}
                    disabled={loading.deletePlaylist}
                    className="bg-amber-400 text-slate-900 hover:bg-amber-500"
                >
                    {loading.deletePlaylist ? (
                        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    ) : (
                        <Plus className="h-4 w-4 mr-2" />
                    )}
                    Delete Playlist
                </Button>
            </div>
            <p className="text-sm text-slate-300 mb-2">{playlist.description}</p>
            <p className="text-sm text-slate-400 mb-4">Genre: {playlist.genre}</p>
            
            <ScrollArea className="h-[200px] w-full rounded-md border border-slate-700 p-4">
                {playlist.songs.map((song) => (
                    <div key={song.id} className="flex justify-between items-center mb-2">
                        <span className="text-slate-300">
                            {song.songName}
                            <span className="text-slate-400 text-sm ml-2">
                                ({Math.floor(song.duration / 60)}:
                                {(song.duration % 60).toString().padStart(2, "0")})
                            </span>
                        </span>
                        <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => onAddToQueue(song)}
                            disabled={loading.addToQueue[song.id]}
                            className="text-amber-400 hover:text-amber-300 text-sm"
                        >
                            {loading.addToQueue[song.id] ? (
                                <Loader2 className="h-4 w-4 animate-spin" />
                            ) : (
                                <Plus className="h-4 w-4" />
                            )}
                            add to queue
                        </Button>
                    </div>
                ))}
            </ScrollArea>
            
            <AddSongForm
                youtubeLink={youtubeLink}
                setYoutubeLink={setYoutubeLink}
                addSong={handleAddSong}
                isAddingSong={loading.addSong}
            />
        </div>
    );
}