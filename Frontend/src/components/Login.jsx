import React, { useState, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthProvider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {Card,CardHeader,CardTitle,CardDescription,CardContent,CardFooter,} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Loader2 } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { authService } from "../service/AuthService";

export default function Login() {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const navigate = useNavigate();
    const location = useLocation();
    const { login, auth } = useAuth();
    const [isLoggingIn, setIsLoggingIn] = useState(false);
    const [isGoogleLoggingIn, setIsGoogleLoggingIn] = useState(false);
    const { toast } = useToast();

    useEffect(() => {
        const handleOAuthResponse = () => {
            const searchParams = new URLSearchParams(window.location.search);
            const data = searchParams.get("data");

            if (data) {
                try {
                    const parsedData = JSON.parse(decodeURIComponent(data));
                    if (parsedData.success) {
                        login({
                            success: true,
                            user: parsedData.user,
                            token: parsedData.token,
                            message: parsedData.message,
                        });
                    } else {
                        toast({
                            title: "Login Failed",
                            description:
                                parsedData.message || "OAuth login failed",
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

    useEffect(() => {
        if (auth.isAuthenticated) {
            const from = location.state?.from?.pathname || "/home";
            navigate(from, { replace: true });
        }
    }, [auth.isAuthenticated, navigate, location]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsLoggingIn(true);
        try {
            const data = await authService.login(email, password);
            login({
                success: true,
                user: data.user,
                token: data.token,
                message: "Login successful",
            });
        } catch (err) {
            toast({
                title: "Login Failed",
                description: err.message || "An error occurred during login",
                variant: "destructive",
            });
            console.error("Login error:", err);
        } finally {
            setIsLoggingIn(false);
        }
    };

    const handleGoogleLogin = () => {
        setIsGoogleLoggingIn(true);
        authService.googleLogin();
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
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="space-y-2">
                            <Label htmlFor="email" className="text-slate-200">
                                Email
                            </Label>
                            <Input
                                id="email"
                                type="text"
                                placeholder="Enter your email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                required
                                className="bg-slate-800 border-slate-700 text-slate-100 placeholder-slate-500"
                            />
                        </div>
                        <div className="space-y-2">
                            <Label
                                htmlFor="password"
                                className="text-slate-200"
                            >
                                Password
                            </Label>
                            <Input
                                id="password"
                                type="password"
                                placeholder="Enter your password"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                required
                                className="bg-slate-800 border-slate-700 text-slate-100 placeholder-slate-500"
                            />
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
                        className="w-full border-slate-700 text-slate-800 hover:bg-slate-800 hover:text-slate-100"
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
