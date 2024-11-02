"use client"

import React, { useEffect, useRef, useState } from "react"
import YouTube from "react-youtube"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Slider } from "@/components/ui/slider"
import { PlayCircle, PauseCircle, SkipForward, Volume2, VolumeX, Loader2 } from "lucide-react"

export default function SyncedPlayer({
  currentSong = { title: "No song playing", youtubeLink: "" },
  isCreator = false,
  client = null,
  roomId = "",
  onSongEnd = () => {},
}) {
  const [isSkipping, setIsSkipping] = useState(false)
  const [isPlaying, setIsPlaying] = useState(true)
  const [isMuted, setIsMuted] = useState(false)
  const [volume, setVolume] = useState(100)
  const [progress, setProgress] = useState(0)
  const [duration, setDuration] = useState(0)
  const playerRef = useRef(null)
  const syncIntervalRef = useRef(null)
  const lastSyncTimeRef = useRef(0)

  useEffect(() => {
    if (client && client.connected) {
      const syncSubscription = client.subscribe(
        `/topic/room/${roomId}/timeSync`,
        (message) => {
          const { currentTime, isPlaying: playState } = JSON.parse(message.body)
          if (playerRef.current && !isCreator) {
            const player = playerRef.current.internalPlayer
            const currentPlayerTime = player.getCurrentTime()
            if (Math.abs(currentPlayerTime - currentTime) > 1.5) {
              player.seekTo(currentTime)
            }
            player[playState ? 'playVideo' : 'pauseVideo']()
            setIsPlaying(playState)
          }
        }
      )

      if (isCreator) {
        syncIntervalRef.current = setInterval(() => {
          if (playerRef.current) {
            const player = playerRef.current.internalPlayer
            player.getCurrentTime().then((currentTime) => {
              if (currentTime - lastSyncTimeRef.current >= 60 || Math.abs(currentTime - lastSyncTimeRef.current) > 1.5) {
                client.publish({
                  destination: `/app/room/${roomId}/timeSync`,
                  body: JSON.stringify({
                    currentTime,
                    isPlaying,
                  }),
                })
                lastSyncTimeRef.current = currentTime
              }
            })
          }
        }, 1000)
      }

      return () => {
        if (syncIntervalRef.current) {
          clearInterval(syncIntervalRef.current)
        }
        if (syncSubscription) {
          syncSubscription.unsubscribe()
        }
      }
    }
  }, [client, roomId, isCreator, isPlaying])

  const handleSkip = () => {
    setIsSkipping(true)
    onSongEnd().finally(() => {
      setIsSkipping(false)
    })
  }

  const onReady = (event) => {
    setDuration(event.target.getDuration())
  }

  const onStateChange = (event) => {
    const newIsPlaying = event.data === YouTube.PlayerState.PLAYING
    if (newIsPlaying !== isPlaying) {
      setIsPlaying(newIsPlaying)
      if (isCreator) {
        client.publish({
          destination: `/app/room/${roomId}/timeSync`,
          body: JSON.stringify({
            currentTime: event.target.getCurrentTime(),
            isPlaying: newIsPlaying,
          }),
        })
      }
    }

    if (event.data === YouTube.PlayerState.ENDED) {
      onSongEnd()
    }
  }

  const handlePlayPause = () => {
    if (playerRef.current) {
      const player = playerRef.current.internalPlayer
      if (isPlaying) {
        player.pauseVideo()
      } else {
        player.playVideo()
      }
      setIsPlaying(!isPlaying)
    }
  }

  const handleVolumeChange = (newVolume) => {
    setVolume(newVolume[0])
    if (playerRef.current) {
      playerRef.current.internalPlayer.setVolume(newVolume[0])
    }
    setIsMuted(newVolume[0] === 0)
  }

  const handleMuteToggle = () => {
    if (playerRef.current) {
      const player = playerRef.current.internalPlayer
      if (isMuted) {
        player.unMute()
        player.setVolume(volume)
      } else {
        player.mute()
      }
      setIsMuted(!isMuted)
    }
  }

  const handleProgressChange = (newProgress) => {
    if (playerRef.current && isCreator) {
      const player = playerRef.current.internalPlayer
      const newTime = (newProgress[0] / 100) * duration
      player.seekTo(newTime)
      setProgress(newProgress[0])
      client.publish({
        destination: `/app/room/${roomId}/timeSync`,
        body: JSON.stringify({
          currentTime: newTime,
          isPlaying,
        }),
      })
    }
  }

  useEffect(() => {
    const progressInterval = setInterval(() => {
      if (playerRef.current && isPlaying) {
        playerRef.current.internalPlayer
          .getCurrentTime()
          .then((currentTime) => {
            setProgress((currentTime / duration) * 100)
          })
      }
    }, 1000)

    return () => clearInterval(progressInterval)
  }, [isPlaying, duration])

  const formatTime = (time) => {
    const minutes = Math.floor(time / 60)
    const seconds = Math.floor(time % 60)
    return `${minutes}:${seconds.toString().padStart(2, "0")}`
  }

  return (
    <Card className="w-full bg-gradient-to-br from-slate-800 to-slate-900 border-slate-700 shadow-xl">
      <CardContent className="p-4 space-y-4">
        <div className="relative aspect-video rounded-lg overflow-hidden">
          <YouTube
            ref={playerRef}
            videoId={currentSong.youtubeLink.split("v=")[1]}
            opts={{
              height: "100%",
              width: "100%",
              playerVars: {
                autoplay: isCreator ? 1 : 0,
                controls: 0,
              },
            }}
            onReady={onReady}
            onStateChange={onStateChange}
            className="absolute inset-0"
          />
        </div>
        {isCreator && (
          <div className="space-y-2">
            <Slider
              value={[progress]}
              max={100}
              step={0.1}
              onValueChange={handleProgressChange}
              className="w-full"
            />
            <div className="flex justify-between text-sm text-amber-400">
              <span>{formatTime((progress / 100) * duration)}</span>
              <span>{formatTime(duration)}</span>
            </div>
          </div>
        )}
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Button
              onClick={handlePlayPause}
              variant="ghost"
              size="icon"
              className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10"
            >
              {isPlaying ? (
                <PauseCircle size={24} />
              ) : (
                <PlayCircle size={24} />
              )}
            </Button>
            <Button
              onClick={handleMuteToggle}
              variant="ghost"
              size="icon"
              className="text-amber-400 hover:text-amber-300 hover:bg-amber-400/10"
            >
              {isMuted ? (
                <VolumeX size={24} />
              ) : (
                <Volume2 size={24} />
              )}
            </Button>
          </div>
          <Slider
            value={[volume]}
            max={100}
            step={1}
            onValueChange={handleVolumeChange}
            className="w-1/3"
          />
          {isCreator && (
            <Button
              onClick={handleSkip}
              size="sm"
              className="bg-amber-400 text-slate-900 hover:bg-amber-500 transition-colors duration-200 w-full sm:w-auto"
              disabled={isSkipping}
            >
              {isSkipping ? (
                <Loader2
                  size={16}
                  className="mr-2 animate-spin"
                />
              ) : (
                <SkipForward size={16} className="mr-2" />
              )}
              {isSkipping ? "Skipping..." : "Skip"}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}