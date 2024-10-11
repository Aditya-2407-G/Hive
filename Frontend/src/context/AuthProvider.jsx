import React, { createContext, useContext, useState, useEffect } from 'react';
import axios from 'axios';

const AuthContext = createContext();

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [auth, setAuth] = useState({
    isAuthenticated: false,
    user: null,
    token: null,
    message: null
  });
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const initializeAuth = async () => {
      const storedToken = localStorage.getItem('authToken');
      const storedUser = localStorage.getItem('authUser');
      
      if (storedToken && storedUser) {
        try {
          const user = JSON.parse(storedUser);
          setAuth({
            isAuthenticated: true,
            user,
            token: storedToken,
            message: null
          });
        } catch (error) {
          console.error('Error parsing stored user data:', error);
          localStorage.removeItem('authToken');
          localStorage.removeItem('authUser');
        }
      }
      setIsLoading(false);
    };

    initializeAuth();
  }, []);

  const login = (response) => {
    if (response.success) {
      const authState = {
        isAuthenticated: true,
        user: response.user,
        token: response.token,
        message: response.message
      };
      
      setAuth(authState);
      localStorage.setItem('authToken', response.token);
      localStorage.setItem('authUser', JSON.stringify(response.user));
    } else {
      setAuth({
        isAuthenticated: false,
        user: null,
        token: null,
        message: response.message || 'Authentication failed'
      });
    }
  };

  const logout = async () => {
    try {
      // Call backend logout endpoint
      await axios.post('http://localhost:8080/api/auth/logout', {}, {
        withCredentials: true,
        headers: {
          Authorization: `Bearer ${auth.token}`
        }
      });
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      // Clear local storage
      localStorage.removeItem('authToken');
      localStorage.removeItem('authUser');
      
      // Reset auth state
      setAuth({
        isAuthenticated: false,
        user: null,
        token: null,
        message: null
      });
    }
  };

  const value = {
    auth,
    login,
    logout,
    isLoading
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};