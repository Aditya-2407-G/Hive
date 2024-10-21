import React from "react";
import {
    BrowserRouter,
    Route,
    Routes,
    Navigate,
} from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthProvider";
import Login from "./components/Login";
import Register from "./components/Register";
import Home from "./components/Home";
import RoomSongs from "./components/RoomSongs";
import LandingPage from "./components/LandingPage";
import { ToastProvider } from "./components/ui/toast";

// ProtectedRoute component
const ProtectedRoute = ({ children }) => {
    const { auth } = useAuth();
    
    if (!auth) {
        return <Navigate to="/login" replace />;
    }
    
    return children;
};

export default function App() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <ToastProvider>
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
                </ToastProvider>
            </AuthProvider>
        </BrowserRouter>
    );
}