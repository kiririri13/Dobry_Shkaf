"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, errorMessage } from "@/lib/api";
import type { Chat, Item, Transfer } from "@/lib/types";
import { useSession } from "./Session";
import RequestStatus from "./RequestStatus";

export default function ItemContact({ item }: { item: Item }) {
  const { user } = useSession();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function start(take: boolean) {
    if (busy) return;
    if (!user) {
      router.push("/login?next=" + encodeURIComponent("/items/" + item.id));
      return;
    }
    setBusy(true);
    setError("");
    try {
      const options = { method: "POST", json: { itemId: item.id } };
      if (take) {
        const transfer = await api<Transfer>("/transfers", options);
        router.push("/profile/transfers/" + transfer.id);
      } else {
        const chat = await api<Chat>("/chats", options);
        router.push("/messages/" + chat.id);
      }
    } catch (failure) {
      setError(errorMessage(failure));
      setBusy(false);
    }
  }

  if (user === undefined || user?.id === item.owner.id || item.status !== "PUBLISHED") return null;
  return <>
    <div className="actions">
      <button type="button" className="secondary" disabled={busy} onClick={() => start(false)}>Написать</button>
      <button type="button" disabled={busy} onClick={() => start(true)}>Хочу забрать</button>
    </div>
    <RequestStatus loading={busy && !error} error={error} />
  </>;
}
