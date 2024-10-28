import React, { useEffect, useCallback } from 'react';
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
import { ThumbsUp, PlayCircle, Trash2, Loader2 } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { useToast } from '@/hooks/use-toast';

const SongQueue = ({
  queuedSongs,
  isCreator,
  handleVote,
  handlePlayNow,
  handleSongDelete,
  loadingVoteIds,
  loadingPlayNowIds,
  loadingDeleteIds,
  stompClient,
  roomId
}) => {
  const { toast } = useToast(); 

  const handleVoteClick = useCallback(async (songId) => {
    try {
      await handleVote(songId, true);
    } catch (error) {
      // Show error toast but don't worry - backend will trigger refresh if needed
      toast({
        title: "Voting Error",
        description: error.message || "You've already voted for this song",
        variant: "destructive"
      });
    }
  }, [handleVote, toast]);

  useEffect(() => {
    if (!stompClient || !roomId) return;

    const subscription = stompClient.subscribe(
      `/topic/room/${roomId}/songs`,
      (message) => {
        const updatedSongs = JSON.parse(message.body);
        // Trigger local state update through parent component
        if (typeof queuedSongs.setQueuedSongs === 'function') {
          queuedSongs.setQueuedSongs(updatedSongs);
        }
      }
    );

    return () => subscription.unsubscribe();
  }, [stompClient, roomId, queuedSongs]);

  return (
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
            {index > 0 && <Separator className="my-2 bg-slate-600" />}
            <div className="flex justify-between items-center py-2">
              <div className="flex-1">
                <h4 className="text-lg font-medium text-amber-300">{song.title}</h4>
                <motion.p
                  className="text-sm text-slate-400"
                  key={`upvotes-${song.id}-${song.upvotes}`}
                  initial={{ scale: 1.2, color: "#fbbf24" }}
                  animate={{ scale: 1, color: "#94a3b8" }}
                  transition={{ duration: 0.3 }}
                >
                  Upvotes: {song.upvotes}
                </motion.p>
              </div>
              <div className="flex items-center space-x-2">
                <Button
                  onClick={() => handleVoteClick(song.id)}
                  variant="ghost"
                  size="sm"
                  className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10 transition-colors duration-200"
                  disabled={loadingVoteIds.includes(song.id)}
                >
                  {loadingVoteIds.includes(song.id) ? (
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
                      className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10 transition-colors duration-200"
                      disabled={loadingPlayNowIds.includes(song.id)}
                    >
                      {loadingPlayNowIds.includes(song.id) ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <PlayCircle className="h-5 w-5" />
                      )}
                    </Button>
                    <Button
                      onClick={() => handleSongDelete(song.id)}
                      variant="ghost"
                      size="sm"
                      className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10 transition-colors duration-200"
                      disabled={loadingDeleteIds.includes(song.id)}
                    >
                      {loadingDeleteIds.includes(song.id) ? (
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
  );
};

export default SongQueue;