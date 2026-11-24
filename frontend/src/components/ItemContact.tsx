"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, errorMessage } from "@/lib/api";
import type { Chat, Item, Transfer } from "@/lib/types";
import { useSession } from "./Session";
import RequestStatus from "./RequestStatus";

export default function ItemContact({ item, action }: { item: Item; action: "message" | "take" }) {
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

  if (user === undefined || user?.id === item.owner.id || item.status !== "PUBLISHED" || item.moderationStatus === "HIDDEN") return null;
  return <>
    {action === "message" ? <button className="item-contact-message" type="button" disabled={busy} onClick={() => start(false)}>
      <img src="/design/message.svg" alt="" />Написать владельцу
    </button> : <button type="button" className="secondary" disabled={busy} onClick={() => start(true)}>
      {item.deliveryMethod === "EXCHANGE" ? "Предложить обмен" : "Хочу забрать"}
    </button>}
    <RequestStatus loading={busy && !error} error={error} />
  </>;
}
