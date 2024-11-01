import React, { useState, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useAuth } from "../context/AuthProvider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Loader2, Eye, EyeOff } from "lucide-react";
import { useToast } from "@/hooks/use-toast";


const loginSchema = z.object({
  email: z.string().email("Invalid email address"),
  password: z.string().min(6, "Password must be at least 6 characters"),
});

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login, auth } = useAuth();
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [isGoogleLoggingIn, setIsGoogleLoggingIn] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const { toast } = useToast();

  const { register, handleSubmit, formState: { errors } } = useForm({
    resolver: zodResolver(loginSchema),
  });

  useEffect(() => {
    if (auth.isAuthenticated) {
      navigate('/home');
    }
  }, [auth.isAuthenticated, navigate]);

  useEffect(() => {
    const handleOAuthResponse = () => {
      const searchParams = new URLSearchParams(window.location.search);
      const data = searchParams.get("data");

      if (data) {
        try {
          const parsedData = JSON.parse(decodeURIComponent(data));
          if (parsedData.message === "Authentication successful") {
            
            login({
              user: parsedData.user.username,
              email: parsedData.user.email,
              message: parsedData.message,
              accessToken: parsedData.tokens.accessToken,
              refreshToken: parsedData.tokens.refreshToken,
            });
          } else {
            toast({
              title: "Login Failed",
              description: parsedData.message || "OAuth login failed",
              variant: "destructive",
            });
          }
        } catch (err) {
          console.error("OAuth response parsing error:", err);
          toast({
            title: "Error",
            description: "Error processing OAuth login response",
            variant: "destructive",
          });
        }
      }
    };

    handleOAuthResponse();
  }, [login, toast]);

  const onSubmit = async (data) => {
    setIsLoggingIn(true);
    
    try {
      const response = await fetch(`${import.meta.env.VITE_BASE_URL}/api/auth/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(data),
        credentials: 'include',
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Login failed');
      }

      const responseData = await response.json();

      
      login({
        user: responseData.user.username,
        message: responseData.message,
        accessToken: responseData.tokens.accessToken,
        refreshToken: responseData.tokens.refreshToken
      });

      toast({
        title: "Login Successful",
        description: "Welcome back!"
      });

      // navigate('/home');
    } catch (err) {
      console.error("Login error:", err);
      toast({
        title: "Login Failed",
        description: err.message || "An error occurred during login. Please check your credentials and try again.",
        variant: "destructive",
      });
    } finally {
      setIsLoggingIn(false);
    }
  };

  const handleGoogleLogin = () => {
    setIsGoogleLoggingIn(true);
    window.location.href = `${import.meta.env.VITE_BASE_URL}/oauth2/authorization/google`;
  };

    return (
        <div className="min-h-screen flex items-center justify-center bg-slate-950">

            <Card className="w-[400px] bg-slate-900 border-slate-800">
                <CardHeader>
                    <CardTitle className="text-2xl font-bold text-slate-100">
                        Welcome back
                    </CardTitle>
                    <CardDescription className="text-slate-400">
                        Sign in to your account
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <form
                        onSubmit={handleSubmit(onSubmit)}
                        className="space-y-4"
                    >
                        <div className="space-y-2">
                            <Label htmlFor="email" className="text-slate-200">
                                Email
                            </Label>
                            <Input
                                id="email"
                                type="text"
                                placeholder="Enter your email"
                                {...register("email")}
                                className="bg-slate-800 border-slate-700 text-slate-100 placeholder-slate-500"
                            />
                            {errors.email && (
                                <p className="text-red-500 text-sm">
                                    {errors.email.message}
                                </p>
                            )}
                        </div>
                        <div className="space-y-2">
                            <Label
                                htmlFor="password"
                                className="text-slate-200"
                            >
                                Password
                            </Label>
                            <div className="relative">
                                <Input
                                    id="password"
                                    type={showPassword ? "text" : "password"}
                                    placeholder="Enter your password"
                                    {...register("password")}
                                    className="bg-slate-800 border-slate-700 text-slate-100 placeholder-slate-500 pr-10"
                                />
                                <button
                                    type="button"
                                    onClick={() =>
                                        setShowPassword(!showPassword)
                                    }
                                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-slate-400 hover:text-slate-300"
                                >
                                    {showPassword ? (
                                        <EyeOff className="h-5 w-5" />
                                    ) : (
                                        <Eye className="h-5 w-5" />
                                    )}
                                </button>
                            </div>
                            {errors.password && (
                                <p className="text-red-500 text-sm">
                                    {errors.password.message}
                                </p>
                            )}
                        </div>

                        <Button
                            type="submit"
                            disabled={isLoggingIn}
                            className="w-full bg-amber-400 text-slate-900 hover:bg-amber-300"
                        >
                            {isLoggingIn ? (
                                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            ) : null}
                            {isLoggingIn ? "Signing in..." : "Sign in"}
                        </Button>
                    </form>

                    <div className="relative my-4">
                        <div className="absolute inset-0 flex items-center">
                            <span className="w-full border-t border-slate-700" />
                        </div>
                        <div className="relative flex justify-center text-xs uppercase">
                            <span className="bg-slate-900 px-2 text-slate-500">
                                Or continue with
                            </span>
                        </div>
                    </div>

                    <Button
                        variant="outline"
                        disabled={isGoogleLoggingIn}
                        className="w-full border-slate-700 text-slate-300 hover:bg-slate-800 hover:text-slate-100"
                        onClick={handleGoogleLogin}
                    >
                        {isGoogleLoggingIn ? (
                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        ) : (
                            <svg className="mr-2 h-4 w-4" viewBox="0 0 24 24">
                                <path
                                    d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                                    fill="#4285F4"
                                />
                                <path
                                    d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                                    fill="#34A853"
                                />
                                <path
                                    d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                                    fill="#FBBC05"
                                />
                                <path
                                    d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                                    fill="#EA4335"
                                />
                            </svg>
                        )}
                        {isGoogleLoggingIn
                            ? "Logging in..."
                            : "Continue with Google"}
                    </Button>
                </CardContent>
                <CardFooter className="flex justify-center">
                    <Button
                        variant="link"
                        onClick={() => navigate("/register")}
                        className="text-amber-400 hover:text-amber-300"
                    >
                        Don't have an account? Sign up
                    </Button>
                </CardFooter>
            </Card>
        </div>
    );
}
