"use client";

import Link from "next/link";
import ChatThread from "./ChatThread";
import { PrivatePage } from "./Session";
import RequestStatus from "./RequestStatus";
import { useData } from "@/lib/useData";
import type { Chat } from "@/lib/types";

export default function MessagesLayout({ id }: { id?: string }) {
  return <PrivatePage><ChatList id={id} /></PrivatePage>;
}

function ChatList({ id }: { id?: string }) {
  const chats = useData<Chat[]>("/chats", 5000);
  return <div className={id ? "messages-layout has-thread" : "messages-layout"}>
    <section className="panel messages-list">
      <h1>Сообщения</h1>
      <RequestStatus loading={chats.loading} error={chats.error} retry={chats.reload} />
      {chats.data?.length === 0 && <p>Диалогов пока нет.</p>}
      <ul className="activity-list">
        {chats.data?.map((chat) => <li key={chat.id}>
          <Link className="message-contact" href={"/messages/" + encodeURIComponent(chat.id)} aria-current={id === chat.id ? "page" : undefined}>
            <span className="chat-avatar" aria-hidden="true" />
            <div className="message-contact-text"><strong>{chat.counterpart.name}</strong><p>{chat.lastMessage?.text || chat.lastMessage?.attachment?.name || chat.item.title}</p></div>
            <div className="message-contact-meta">
              {chat.lastMessage && <time dateTime={chat.lastMessage.sentAt}>{new Date(chat.lastMessage.sentAt).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" })}</time>}
              {chat.unreadCount > 0 && <span className="badge" aria-label={`Непрочитанных: ${chat.unreadCount}`}>{chat.unreadCount}</span>}
            </div>
          </Link>
        </li>)}
      </ul>
    </section>
    {id ? <ChatThread key={id} id={id} /> : <p className="messages-empty">Выберите диалог</p>}
  </div>;
}
