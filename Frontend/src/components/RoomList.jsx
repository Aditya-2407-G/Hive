import React from 'react'
import { Card, CardContent } from "@/components/ui/card"
import { LinkIcon } from 'lucide-react'
import { motion } from "framer-motion"

function RoomList({ rooms, viewSongs }) {
  return (
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
                {typeof room.name === 'string' ? room.name : JSON.parse(room.name).roomName}
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
  )
}

export default RoomList