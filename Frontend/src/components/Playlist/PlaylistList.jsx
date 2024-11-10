import { Loader2, Music } from "lucide-react";
import { Button } from "../ui/button";
import { ScrollArea } from "../ui/scroll-area";

export default function PlaylistList({ playlists, loading, onSelectPlaylist }) {
    return (
        <ScrollArea className="h-[300px] w-full rounded-md border border-slate-700 p-4">
            {loading ? (
                <div className="flex justify-center items-center h-full">
                    <Loader2 className="h-6 w-6 animate-spin text-amber-400" />
                </div>
            ) : (
                playlists.map((playlist) => (
                    <Button
                        key={playlist.id}
                        variant="ghost"
                        className="w-full justify-start text-left font-normal mb-2"
                        onClick={() => onSelectPlaylist(playlist)}
                    >
                        <Music className="mr-2 h-4 w-4" />
                        {playlist.name}
                    </Button>
                ))
            )}
        </ScrollArea>
    );
}