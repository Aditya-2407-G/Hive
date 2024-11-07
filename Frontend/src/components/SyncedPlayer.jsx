'use client'

import React, { useEffect, useRef, useState, useCallback } from "react"
import YouTube from "react-youtube"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Slider } from "@/components/ui/slider"
import { PlayCircle, PauseCircle, SkipForward, Volume2, VolumeX, Loader2 } from "lucide-react"

export default function SyncedPlayer({
  currentSong = { title: "No song playing", youtubeLink: "" },
  isCreator = false,
  client,
  roomId,
  onSongEnd,
}) {
  const [playerState, setPlayerState] = useState({
    isSkipping: false,
    isPlaying: true,
    isMuted: false,
    volume: 100,
    progress: 0,
    duration: 0,
  })
  const playerRef = useRef(null)
  const syncIntervalRef = useRef(null)
  const lastSyncTimeRef = useRef(0)
  const isSyncingRef = useRef(false)

  const updatePlayerState = (newState) => {
    setPlayerState(prevState => ({ ...prevState, ...newState }))
  }

  const publishTimeSync = useCallback((currentTime, isPlaying) => {
    client?.publish({
      destination: `/app/room/${roomId}/timeSync`,
      body: JSON.stringify({ currentTime, isPlaying }),
    })
  }, [client, roomId])

  const handleTimeSync = useCallback((message) => {
    const { currentTime, isPlaying: playState } = JSON.parse(message.body)
    const player = playerRef.current?.internalPlayer
    if (!player || isCreator) return

    player.getCurrentTime().then((currentPlayerTime) => {
      if (Math.abs(currentPlayerTime - currentTime) > 10) {
        isSyncingRef.current = true
        player.seekTo(currentTime, true)
        if (playState && player.getPlayerState() !== YouTube.PlayerState.PLAYING) {
          player.playVideo()
        }
        setTimeout(() => { isSyncingRef.current = false }, 1000)
      }
    })

    updatePlayerState({ isPlaying: playState })
  }, [isCreator])

  useEffect(() => {
    if (!client?.connected) return

    const syncSubscription = client.subscribe(`/topic/room/${roomId}/timeSync`, handleTimeSync)

    if (isCreator) {
      syncIntervalRef.current = setInterval(() => {
        const player = playerRef.current?.internalPlayer
        if (!player) return

        player.getCurrentTime().then((currentTime) => {
          if (currentTime - lastSyncTimeRef.current >= 5) {
            publishTimeSync(currentTime, playerState.isPlaying)
            lastSyncTimeRef.current = currentTime
          }
        })
      }, 1000)
    }

    return () => {
      syncSubscription?.unsubscribe()
      if (syncIntervalRef.current) clearInterval(syncIntervalRef.current)
    }
  }, [client, roomId, isCreator, playerState.isPlaying, handleTimeSync, publishTimeSync])

  const handleSkip = async () => {
    updatePlayerState({ isSkipping: true })
    await onSongEnd()
    updatePlayerState({ isSkipping: false })
  }

  const onReady = (event) => {
    updatePlayerState({ duration: event.target.getDuration() })
    event.target.playVideo()
  }

  const onStateChange = (event) => {
    if (isSyncingRef.current) return

    const newIsPlaying = event.data === YouTube.PlayerState.PLAYING || 
                         event.data === YouTube.PlayerState.BUFFERING
    updatePlayerState({ isPlaying: newIsPlaying })

    if (isCreator && newIsPlaying !== playerState.isPlaying) {
      publishTimeSync(event.target.getCurrentTime(), newIsPlaying)
    }

    if (event.data === YouTube.PlayerState.ENDED) {
      onSongEnd()
    }
  }

  const handlePlayPause = () => {
    const player = playerRef.current?.internalPlayer
    if (!player) return

    const newIsPlaying = !playerState.isPlaying
    player[newIsPlaying ? 'playVideo' : 'pauseVideo']()
    updatePlayerState({ isPlaying: newIsPlaying })

    if (isCreator) {
      player.getCurrentTime().then((currentTime) => {
        publishTimeSync(currentTime, newIsPlaying)
      })
    }
  }

  const handleVolumeChange = (newVolume) => {
    const volume = newVolume[0]
    updatePlayerState({ volume, isMuted: volume === 0 })
    playerRef.current?.internalPlayer?.setVolume(volume)
  }

  const handleMuteToggle = () => {
    const player = playerRef.current?.internalPlayer
    if (!player) return

    const newIsMuted = !playerState.isMuted
    player[newIsMuted ? 'mute' : 'unMute']()
    player.setVolume(newIsMuted ? 0 : playerState.volume)
    updatePlayerState({ isMuted: newIsMuted })
  }

  const handleProgressChange = (newProgress) => {
    if (!isCreator || !playerRef.current?.internalPlayer) return

    const newTime = (newProgress[0] / 100) * playerState.duration
    playerRef.current.internalPlayer.seekTo(newTime)
    updatePlayerState({ progress: newProgress[0] })
    publishTimeSync(newTime, playerState.isPlaying)
  }

  useEffect(() => {
    const progressInterval = setInterval(() => {
      if (playerRef.current?.internalPlayer && playerState.isPlaying) {
        playerRef.current.internalPlayer.getCurrentTime().then((currentTime) => {
          updatePlayerState({ progress: (currentTime / playerState.duration) * 100 })
        })
      }
    }, 1000)

    return () => clearInterval(progressInterval)
  }, [playerState.isPlaying, playerState.duration])

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
                autoplay: 1,
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
              value={[playerState.progress]}
              max={100}
              step={0.1}
              onValueChange={handleProgressChange}
              className="w-full"
            />
            <div className="flex justify-between text-sm text-amber-400">
              <span>{formatTime((playerState.progress / 100) * playerState.duration)}</span>
              <span>{formatTime(playerState.duration)}</span>
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
              {playerState.isPlaying ? (
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
              {playerState.isMuted ? (
                <VolumeX size={24} />
              ) : (
                <Volume2 size={24} />
              )}
            </Button>
          </div>
          <Slider
            value={[playerState.volume]}
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
              disabled={playerState.isSkipping}
            >
              {playerState.isSkipping ? (
                <Loader2 size={16} className="mr-2 animate-spin" />
              ) : (
                <SkipForward size={16} className="mr-2" />
              )}
              {playerState.isSkipping ? "Skipping..." : "Skip"}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}