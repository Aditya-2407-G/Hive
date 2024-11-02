import React from 'react';
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Music, Loader2 } from "lucide-react";

const cleanYoutubeLink = (url) => {
  if (!url) return "";
  
  // Convert URL to string and trim whitespace
  let cleanUrl = url.toString().trim();
  
  // Convert mobile and music URLs to standard youtube.com
  cleanUrl = cleanUrl.replace("m.youtube.com", "youtube.com")
                    .replace("music.youtube.com", "youtube.com");
  
  // Extract video ID based on different URL patterns
  let videoId = '';
  
  // Pattern for standard youtube.com/watch?v= links
  if (cleanUrl.includes('watch?v=')) {
    videoId = cleanUrl.split('watch?v=')[1];
  }
  // Pattern for youtu.be/ links
  else if (cleanUrl.includes('youtu.be/')) {
    videoId = cleanUrl.split('youtu.be/')[1];
  }
  // Pattern for youtube.com/v/ links
  else if (cleanUrl.includes('youtube.com/v/')) {
    videoId = cleanUrl.split('youtube.com/v/')[1];
  }
  // Pattern for youtube.com/embed/ links
  else if (cleanUrl.includes('youtube.com/embed/')) {
    videoId = cleanUrl.split('youtube.com/embed/')[1];
  }
  
  // If we found a video ID, clean it up
  if (videoId) {
    // Remove any parameters after the video ID
    videoId = videoId.split('&')[0];
    videoId = videoId.split('?')[0];
    videoId = videoId.split('#')[0];
    
    // Reconstruct the clean URL
    return `https://www.youtube.com/watch?v=${videoId}`;
  }
  
  return cleanUrl;
};

export default function AddSongForm({ youtubeLink, setYoutubeLink, addSong, isAddingSong }) {
  const handleSubmit = (e) => {
    e.preventDefault();
    if (!youtubeLink) return;
    
    // Clean the URL before submitting
    const cleanedLink = cleanYoutubeLink(youtubeLink);
    setYoutubeLink(cleanedLink);
    addSong();
  };

  const handleInputChange = (e) => {
    setYoutubeLink(e.target.value);
  };

  const handlePaste = (e) => {
    // Clean the URL immediately on paste
    const pastedContent = e.clipboardData.getData('text');
    e.preventDefault();
    const cleanedLink = cleanYoutubeLink(pastedContent);
    setYoutubeLink(cleanedLink);
  };

  return (
    <Card className="bg-slate-700 border-slate-600 mb-4 shadow-lg">
      <CardHeader>
        <CardTitle className="text-2xl text-amber-400">
          Add a Song
        </CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="flex flex-col sm:flex-row space-y-2 sm:space-y-0 sm:space-x-2">
          <Input
            type="text"
            value={youtubeLink}
            onChange={handleInputChange}
            onPaste={handlePaste}
            placeholder="Enter YouTube link"
            className="bg-slate-600 border-slate-500 text-slate-100 placeholder-slate-400 flex-grow"
            disabled={isAddingSong}
          />
          <Button
            type="submit"
            disabled={isAddingSong || !youtubeLink}
            className="bg-amber-400 text-slate-900 hover:bg-amber-500 transition-colors duration-200 w-full sm:w-auto"
          >
            {isAddingSong ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Music className="mr-2 h-4 w-4" />
            )}
            {isAddingSong ? "Adding..." : "Add Song"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}