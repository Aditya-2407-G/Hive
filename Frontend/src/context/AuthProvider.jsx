import React, { createContext, useState, useContext, useEffect } from 'react';
import axios from 'axios';
import { useToast } from "@/hooks/use-toast";

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
    message: null,
    accessToken: null,
    refreshToken: null,
  });
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const initializeAuth = async () => {
      const storedUser = localStorage.getItem('authUser');
      const storedAccessToken = localStorage.getItem('accessToken');
      const storedRefreshToken = localStorage.getItem('refreshToken');
      
      if (storedUser && storedAccessToken && storedRefreshToken) {
        try {
          const user = JSON.parse(storedUser);
          setAuth({
            isAuthenticated: true,
            user,
            message: null,
            accessToken: storedAccessToken,
            refreshToken: storedRefreshToken,
          });
        } catch (error) {
          console.error('Error parsing stored user data:', error);
          localStorage.removeItem('authUser');
          localStorage.removeItem('accessToken');
          localStorage.removeItem('refreshToken');
        }
      }
      setIsLoading(false);
    };

    initializeAuth();
  }, []);

  const login = ({user, message, accessToken, refreshToken}) => {
    setAuth({
      isAuthenticated: true,
      user: user,
      message: message,
      accessToken: accessToken,
      refreshToken: refreshToken,
    });

    localStorage.setItem('authUser', JSON.stringify(user));
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
  };

  const logout = async () => {
    try {
      await axios.post('http://localhost:8080/api/auth/logout', {}, {
        withCredentials: true,
      });
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      setAuth({
        isAuthenticated: false,
        user: null,
        message: null,
        accessToken: null,
        refreshToken: null,
      });
      localStorage.removeItem('authUser');
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
    }
  };

  const refreshToken = async () => {
    try {
      const response = await axios.post('http://localhost:8080/api/auth/refresh', {}, {
        withCredentials: true,
      });
      
      if (response.data && response.data.accessToken && response.data.refreshToken) {
        setAuth(prevAuth => ({
          ...prevAuth,
          accessToken: response.data.accessToken,
          refreshToken: response.data.refreshToken,
        }));
        localStorage.setItem('accessToken', response.data.accessToken);
        localStorage.setItem('refreshToken', response.data.refreshToken);
        return true;
      }
      return false;
    } catch (error) {
      console.error('Refresh token error:', error);
      return false;
    }
  };

  const values = {
    auth,
    login,
    logout,
    refreshToken,
    isLoading,
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  return (
    <AuthContext.Provider value={values}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuthInterceptor = () => {
  const { auth, refreshToken, logout } = useAuth();
  const { toast } = useToast();

  useEffect(() => {
    const requestInterceptor = axios.interceptors.request.use(
      (config) => {
        if (auth?.accessToken) {
          config.headers['Authorization'] = `Bearer ${auth.accessToken}`;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    const responseInterceptor = axios.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config;
        if (error.response?.status === 401 && !originalRequest._retry) {
          originalRequest._retry = true;
          try {
            console.log('Refreshing token');
            const refreshSuccessful = await refreshToken();
            if (refreshSuccessful) {
              originalRequest.headers['Authorization'] = `Bearer ${auth.accessToken}`;
              return axios(originalRequest);
            } else {
              console.log('Token refresh failed');
              throw new Error('Token refresh failed');
            }
          } catch (refreshError) {
            await logout();
            toast({
              title: 'Session Expired',
              description: 'Please log in again',
              variant: 'destructive',
            });
            return Promise.reject(refreshError);
          }
        }
        return Promise.reject(error);
      }
    );

    return () => {
      axios.interceptors.request.eject(requestInterceptor);
      axios.interceptors.response.eject(responseInterceptor);
    };
  }, [auth, refreshToken, logout, toast]);
};