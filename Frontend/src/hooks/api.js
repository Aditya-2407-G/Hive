import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthProvider';
import { useToast } from '@/hooks/use-toast';

const createAxiosInstance = (navigate, logout, toast) => {
  const instance = axios.create({
    baseURL: `https://${import.meta.env.VITE_BASE_URL}/api`,
    withCredentials: true,
    headers: {
      'Content-Type': 'application/json',
    },
  });


  instance.interceptors.request.use(
    (config) => {
      const token = localStorage.getItem('accessToken');
      if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
      }
      return config;
    },
    (error) => Promise.reject(error)
  );

  instance.interceptors.response.use(
    (response) => response,
    async (error) => {
      if (error.response && error.response.status === 401) {
        toast({
          variant: 'destructive',
          title: 'Session Expired',
          description: 'Please log in again',
        });
        await logout();
        navigate('/login', { state: { message: 'Your session has expired. Please log in again.' } });
      }
      return Promise.reject(error);
    }
  );

  return instance;
};

export const useApi = () => {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const { toast } = useToast();

  return createAxiosInstance(navigate, logout, toast);
};