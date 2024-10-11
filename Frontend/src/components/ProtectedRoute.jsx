import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthProvider";

export const ProtectedRoute = ({ children }) => {
    const { auth, isLoading } = useAuth();
    const location = useLocation();

    if (isLoading) {
        return (
            <div className="min-h-screen flex items-center justify-center">
                <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500"></div>
            </div>
        );
    }

    if (!auth.isAuthenticated) {
        // Redirect to login page but save the attempted URL
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    return children;
};
