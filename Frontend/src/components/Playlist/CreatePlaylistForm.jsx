import { Loader2, Plus } from "lucide-react";
import { Button } from "../ui/button";
import { Input } from "../ui/input";
import { useState } from "react";

export default function CreatePlaylistForm({ onSubmit, isLoading }) {
    const [formData, setFormData] = useState({
        name: "",
        description: "",
        genre: "",
    });

    const handleSubmit = async (e) => {
        e.preventDefault();
        const success = await onSubmit(formData);
        if (success) {
            setFormData({ name: "", description: "", genre: "" });
        }
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-4">
            {["name", "description", "genre"].map((field) => (
                <Input
                    key={field}
                    type="text"
                    placeholder={field.charAt(0).toUpperCase() + field.slice(1)}
                    value={formData[field]}
                    onChange={(e) =>
                        setFormData({
                            ...formData,
                            [field]: e.target.value,
                        })
                    }
                    className="w-full bg-slate-700 text-slate-100 border-slate-600"
                />
            ))}
            <Button
                type="submit"
                disabled={isLoading}
                className="w-full bg-amber-400 text-slate-900 hover:bg-amber-500"
            >
                {isLoading ? (
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                ) : (
                    <Plus className="h-4 w-4 mr-2" />
                )}
                Create Playlist
            </Button>
        </form>
    );
}