import React from 'react';
import { BrowserRouter as Router, Route, Routes, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthProvider';
import Login from './components/Login';
import Register from './components/Register';
import Home from './components/Home';
import RoomSongs from './components/RoomSongs';
import { ProtectedRoute } from './components/ProtectedRoute';
import LandingPage from './components/LandingPage';
import { ToastProvider } from './components/ui/toast';

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <Router>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/oauth/callback" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/" element={<LandingPage />} />
            <Route 
              path="/home" 
              element={
                <ProtectedRoute>
                  <Home />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="/rooms/:roomId" 
              element={
                <ProtectedRoute>
                  <RoomSongs />
                </ProtectedRoute>
              } 
            />
          </Routes>
        </Router>
      </ToastProvider>
    </AuthProvider>
  );
}