"use client";

import { useState } from "react";
import Link from "next/link";
import { api, errorMessage } from "@/lib/api";
import type { Favorites } from "@/lib/types";
import { useData } from "@/lib/useData";
import RequestStatus from "./RequestStatus";
import { useSession } from "./Session";

export const favoritesPath = "/profile/me/favorites";

export function FavoriteButton({ id, type, saved, onChange, compact, disabled }: {
  id: string;
  type: "ITEMS" | "POINTS";
  saved: boolean;
  onChange: (saved: boolean) => void;
  compact?: boolean;
  disabled?: boolean;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function toggle() {
    setBusy(true);
    setError("");
    try {
      await api(favoritesPath + "/" + type + "/" + encodeURIComponent(id), { method: saved ? "DELETE" : "PUT" });
      onChange(!saved);
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <div>
    <button type="button" className={compact ? "favorite-heart" : "secondary"} disabled={busy || disabled}
      onClick={toggle} aria-label={saved ? "Убрать из избранного" : "В избранное"}>
      {compact ? <img src="/design/favorite-saved.png" alt="" /> : saved ? "Убрать из избранного" : "В избранное"}
    </button>
    <RequestStatus error={error} />
  </div>;
}

export function FavoriteControl({ id, type }: { id: string; type: "ITEMS" | "POINTS" }) {
  const { user } = useSession();
  const favorites = useData<Favorites>(user ? favoritesPath : "");
  const [saved, setSaved] = useState<boolean>();
  const href = (type === "ITEMS" ? "/items/" : "/points/") + id;

  if (user === null) return <Link className="button secondary" href={"/login?next=" + encodeURIComponent(href)}>В избранное</Link>;
  if (!favorites.data) return <RequestStatus loading={favorites.loading} error={favorites.error} retry={favorites.reload} />;
  const entries = type === "ITEMS" ? favorites.data.items : favorites.data.points;
  return <FavoriteButton id={id} type={type} saved={saved ?? entries.some((entry) => entry.id === id)} onChange={setSaved} />;
}
