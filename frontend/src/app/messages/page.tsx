"use client";

import Link from "next/link";
import { PrivatePage } from "@/components/Session";
import RequestStatus from "@/components/RequestStatus";
import { useData } from "@/lib/useData";
import type { Chat } from "@/lib/types";

export default function Messages() {
  return <PrivatePage><ChatList /></PrivatePage>;
}

function ChatList() {
  const chats = useData<Chat[]>("/chats", 5000);
  return <>
    <h1>Сообщения</h1>
    <RequestStatus loading={chats.loading} error={chats.error} retry={chats.reload} />
    {chats.data?.length === 0 && <p>Диалогов пока нет. Открыть диалог можно из карточки вещи.</p>}
    <ul className="activity-list">
      {chats.data?.map((chat) => <li key={chat.id}>
        <Link className="panel activity-link" href={"/messages/" + chat.id}>
          <strong>{chat.counterpart.name}</strong> · {chat.item.title}
          {chat.unreadCount > 0 && <span className="badge">Новых: {chat.unreadCount}</span>}
          <p>{chat.lastMessage?.text || "Сообщений пока нет"}</p>
        </Link>
      </li>)}
    </ul>
  </>;
}
