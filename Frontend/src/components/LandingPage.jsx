import React from 'react';
import { Button } from "@/components/ui/button"
import { MusicIcon, UsersIcon, ThumbsUpIcon, ShareIcon } from "lucide-react"
import { Link } from 'react-router-dom';

const FeatureCard = ({ icon, title, description }) => (
  <div className="bg-slate-900 p-6 rounded-lg text-center border border-slate-800">
    {icon}
    <h3 className="text-xl font-semibold mb-2 text-slate-100">{title}</h3>
    <p className="text-slate-300">{description}</p>
  </div>
);

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-slate-950 text-white">
      <header className="container mx-auto px-4 py-6 flex justify-between items-center">
        <h1 className="text-3xl font-bold text-amber-400">Hive</h1>
        <nav>
          <Button variant="ghost" className="text-slate-200 hover:text-white mr-4"><Link to= '/login'>Login</Link></Button>
          <Button  className="bg-amber-400 text-slate-900 hover:bg-amber-300">
            <Link to= '/register'>Sign Up</Link>
          </Button>
        </nav>
      </header>

      <main className="container mx-auto px-4 py-16">
        <div className="text-center mb-16">
          <h2 className="text-5xl font-bold mb-4 text-slate-100">Share Music, Connect with Friends</h2>
          <p className="text-xl text-slate-300 mb-8">Create rooms, queue songs, and enjoy music together in real-time</p>
          <Button
          className="bg-amber-400 text-slate-900 hover:bg-amber-300 text-lg px-8 py-4">
            
            <Link to= "/register">Get Started</Link>
            </Button>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-4 gap-8 mb-16">
          <FeatureCard
            icon={<MusicIcon className="w-12 h-12 mb-4 text-amber-400" />}
            title="Create Music Rooms"
            description="Set up unique rooms for different playlists or moods"
          />
          <FeatureCard
            icon={<UsersIcon className="w-12 h-12 mb-4 text-amber-400" />}
            title="Invite Friends"
            description="Share room links with friends to join the music session"
          />
          <FeatureCard
            icon={<ThumbsUpIcon className="w-12 h-12 mb-4 text-amber-400" />}
            title="Vote on Songs"
            description="Upvote or downvote songs to decide what plays next"
          />
          <FeatureCard
            icon={<ShareIcon className="w-12 h-12 mb-4 text-amber-400" />}
            title="Collaborative Queues"
            description="Add YouTube links to contribute to the playlist"
          />
        </div>
      </main>

      <footer className="bg-slate-900 py-8">
        <div className="container mx-auto px-4 text-center text-slate-400">
          <p>&copy; 2023 Hive. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}