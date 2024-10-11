const API_BASE_URL = 'http://localhost:8080/api/auth';

export const authService = {
    login: async (email, password) => {
        const response = await fetch(`${API_BASE_URL}/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ email, password }),
            credentials: 'include',
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || 'Login failed');
        }

        return response.json();
    },

    googleLogin: () => {
        window.location.href = 'http://localhost:8080/oauth2/authorization/google';
    },

    // You can add more auth-related API calls here
    register: async (userData) => {
        const response = await fetch(`${API_BASE_URL}/register`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(userData),
            credentials: 'include',
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || 'Registration failed');
        }

        return response.json();
    },
};