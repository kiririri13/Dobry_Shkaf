"use client";

import Link from "next/link";
import { useState } from "react";
import { PrivatePage, useSession } from "@/components/Session";
import RequestStatus from "@/components/RequestStatus";
import ItemList from "@/components/ItemList";
import { api, errorMessage } from "@/lib/api";

export default function ProfilePage() {
  const { user } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function logout() {
    setBusy(true);
    setError("");
    try {
      await api("/auth/logout", { method: "POST" });
      window.location.replace("/");
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <PrivatePage>
    <p className="breadcrumbs"><Link href="/">Главная</Link> / Профиль</p>
    <section className="profile">
      <div className="profile-cover">
        <button className="secondary" onClick={logout} disabled={busy}>{busy ? "Выходим…" : "Выйти"}</button>
      </div>
      <div className="profile-info">
        <span className="profile-avatar" aria-hidden="true">{user?.name.trim().charAt(0).toUpperCase()}</span>
        <div><h1>{user?.name}</h1><p>{user?.email}</p></div>
      </div>
      <RequestStatus error={error} />
    </section>
    <section className="profile-items"><ItemList mine /></section>
  </PrivatePage>;
}
