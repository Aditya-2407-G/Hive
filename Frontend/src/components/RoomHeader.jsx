import React from 'react';
import { CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Music, Users } from "lucide-react";

export default function RoomHeader({
  roomName,
  roomId,
  activeUsers,
  isCreator,
  onLeaveRoom,
  onDeleteRoom
}) {
  return (
    <CardHeader className="border-b border-slate-700">
      <CardTitle className="text-3xl font-bold text-amber-400 flex flex-col sm:flex-row items-center justify-between">
        <div className="flex items-center mb-4 sm:mb-0">
          <Music className="mr-2" /> {roomName || `Room ${roomId}`}
        </div>
        <div className="flex items-center space-x-4">
          <Badge variant="secondary" className="text-sm bg-slate-700 text-amber-400">
            <Users className="w-4 h-4 mr-1" />
            {activeUsers} active
          </Badge>
          <Button
            onClick={onLeaveRoom}
            variant="destructive"
            className="bg-red-600 hover:bg-red-700 text-white transition-colors duration-200"
          >
            Leave Room
          </Button>
          {isCreator && (
            <Button
              onClick={onDeleteRoom}
              variant="destructive"
              className="bg-red-600 hover:bg-red-700 text-white transition-colors duration-200"
            >
              Delete Room
            </Button>
          )}
        </div>
      </CardTitle>
    </CardHeader>
  );
}