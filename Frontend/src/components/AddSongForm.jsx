import React from 'react';
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Music, Loader2 } from "lucide-react";

export default function AddSongForm({ youtubeLink, setYoutubeLink, addSong, isAddingSong }) {
  return (
    <Card className="bg-slate-700 border-slate-600 mb-4 shadow-lg">
      <CardHeader>
        <CardTitle className="text-2xl text-amber-400">
          Add a Song
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col sm:flex-row space-y-2 sm:space-y-0 sm:space-x-2">
        <Input
          type="text"
          value={youtubeLink}
          onChange={(e) => setYoutubeLink(e.target.value)}
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
          {isAddingSong ? "Adding..." : "Add Song"}
        </Button>
      </CardContent>
    </Card>
  );
}