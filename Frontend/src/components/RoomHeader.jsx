import React from 'react'
import { CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Music, Users, LogOut, Trash2 } from "lucide-react"

export default function RoomHeader({
  roomName = "Music Room",
  roomId = "1234",
  activeUsers = 0,
  isCreator = false,
  onLeaveRoom = () => {},
  onDeleteRoom = () => {}
}) {
  return (
    <CardHeader className="border-b border-slate-700 p-4">
      <div className="flex justify-between items-center">
        <div className="space-y-1">
          <CardTitle className="flex items-center text-2xl font-bold text-amber-400">
            <Music className="w-7 h-7 mr-2 flex-shrink-0" />
            <span className="truncate max-w-[100x] sm:max-w-none text-3xl">
              {roomName || `Room ${roomId}`}
            </span>
          </CardTitle>
        </div>
          <Badge variant="secondary" className="bg-slate-700 text-amber-400">
            <Users className="w-4 h-4 mr-1" />
            <span>{activeUsers} active</span>
          </Badge>
        <div className="flex flex-col sm:flex-row gap-2">
          <Button
            onClick={onLeaveRoom}
            variant="destructive"
            className="bg-red-600 hover:bg-red-700 text-white transition-colors duration-200"
          >
            <LogOut className="w-4 h-4 mr-2" />
            <span className="hidden sm:inline">Leave</span>
          </Button>
          {isCreator && (
            <Button
              onClick={onDeleteRoom}
              variant="destructive"
              className="bg-red-600 hover:bg-red-700 text-white transition-colors duration-200"
            >
              <Trash2 className="w-4 h-4 mr-2" />
              <span className="hidden sm:inline">Delete</span>
            </Button>
          )}
        </div>
      </div>
    </CardHeader>
  )
}