import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthProvider';
import axios from 'axios';

function OAuthCallbackHandler() {
  const navigate = useNavigate();
  const { login } = useAuth();

  useEffect(() => {
    const handleOAuthCallback = async () => {
      try {
        const response = await axios.get('http://localhost:8080/oauth2/callback', {
          withCredentials: true,
        });
        
        if (response.data && response.data.token) {
          login(response.data.token);
          navigate('/home');
        } else {
          throw new Error('No token received');
        }
      } catch (error) {
        console.error('Error during OAuth callback:', error);
        navigate('/login');
      }
    };

    handleOAuthCallback();
  }, [navigate, login]);

  return <div>Processing authentication...</div>;
}

export default OAuthCallbackHandler;