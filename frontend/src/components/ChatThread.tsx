"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Chat, ChatMessage, MessagePage } from "@/lib/types";
import RequestStatus from "./RequestStatus";
import { useSession } from "./Session";

function mergeMessages(previous: ChatMessage[], incoming: ChatMessage[]) {
  return [...new Map([...previous, ...incoming].map((message) => [message.seq, message])).values()]
    .sort((first, second) => first.seq - second.seq);
}

export default function ChatThread({ id }: { id: string }) {
  const { user } = useSession();
  const path = "/chats/" + encodeURIComponent(id);
  const chat = useData<Chat>(path);
  const history = useData<MessagePage>(chat.data ? path + "/messages" : "", 4000);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [hasOlder, setHasOlder] = useState<boolean>();
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [error, setError] = useState("");
  const draft = useRef<{ text: string; requestId: string } | null>(null);
  const readThrough = useRef(0);
  const latest = history.data?.messages;
  const unreadSeq = latest?.findLast((message) => message.senderId !== user?.id && !message.readAt)?.seq;

  useEffect(() => {
    if (!history.data) return;
    const { messages: incoming, hasMore } = history.data;
    setMessages((previous) => mergeMessages(previous, incoming));
    setHasOlder((previous) => previous ?? hasMore);
  }, [history.data]);

  useEffect(() => {
    if (!unreadSeq || unreadSeq <= readThrough.current) return;
    readThrough.current = unreadSeq;
    void api(path + "/read", { method: "POST", json: { upToSeq: unreadSeq } }).catch(() => {
      if (readThrough.current === unreadSeq) readThrough.current = 0;
    });
  }, [path, unreadSeq, latest]);

  async function loadOlder() {
    const first = messages[0];
    if (!first || loadingOlder) return;
    setLoadingOlder(true);
    setError("");
    try {
      const page = await api<MessagePage>(path + "/messages?beforeSeq=" + first.seq);
      setMessages((previous) => mergeMessages(page.messages, previous));
      setHasOlder(page.hasMore);
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setLoadingOlder(false);
    }
  }

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = text.trim();
    if (!message || sending) return;
    if (draft.current?.text !== message) draft.current = { text: message, requestId: crypto.randomUUID() };
    setSending(true);
    setError("");
    try {
      const saved = await api<ChatMessage>(path + "/messages", { method: "POST", json: draft.current });
      setMessages((previous) => mergeMessages(previous, [saved]));
      setText("");
      draft.current = null;
      history.reload();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setSending(false);
    }
  }

  return <section className="panel conversation">
    <p className="breadcrumbs"><Link href="/messages">К диалогам</Link></p>
    <RequestStatus loading={chat.loading} error={chat.error} retry={chat.reload} />
    {chat.data && <>
      <h1>{chat.data.counterpart.name}</h1>
      <p><Link href={"/items/" + chat.data.item.id}>{chat.data.item.title}</Link></p>
      <RequestStatus loading={history.loading && !messages.length} error={history.error} retry={history.reload} />
      {hasOlder && <button type="button" className="secondary" disabled={loadingOlder} onClick={loadOlder}>Более ранние сообщения</button>}
      {!history.loading && !history.error && !messages.length && <p>Сообщений пока нет.</p>}
      <ol className="activity-list chat-history" aria-label="История сообщений">
        {messages.map((message) => <li key={message.id} className={message.senderId === user?.id ? "own-message" : ""}>
          <p>{message.text}</p>
          <small><time dateTime={message.sentAt}>{new Date(message.sentAt).toLocaleString("ru-RU")}</time>
            {message.senderId === user?.id && (message.readAt ? " · Прочитано" : " · Отправлено")}
          </small>
        </li>)}
      </ol>
      <form onSubmit={send}>
        <label>Сообщение<textarea rows={3} value={text} maxLength={2000} disabled={sending} required onChange={(event) => setText(event.target.value)} /></label>
        <button disabled={sending || !text.trim()}>{sending ? "Отправляем…" : "Отправить"}</button>
      </form>
      <RequestStatus error={error} />
    </>}
  </section>;
}
