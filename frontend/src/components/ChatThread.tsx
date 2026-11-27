"use client";

import Link from "next/link";
import { useEffect, useLayoutEffect, useRef, useState, type FormEvent } from "react";
import { api, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Chat, ChatMessage, MessagePage } from "@/lib/types";
import ChatAttachmentPreview, { attachmentPreviewType } from "./ChatAttachmentPreview";
import RequestStatus from "./RequestStatus";
import { useSession } from "./Session";
import { ChatTransfer } from "./Transfers";

function mergeMessages(previous: ChatMessage[], incoming: ChatMessage[]) {
  return [...new Map([...previous, ...incoming].map((message) => [message.seq, message])).values()]
    .sort((first, second) => first.seq - second.seq);
}

const dayFormat = new Intl.DateTimeFormat("ru-RU", { day: "numeric", month: "long" });
const timeFormat = new Intl.DateTimeFormat("ru-RU", { hour: "2-digit", minute: "2-digit" });

export default function ChatThread({ id }: { id: string }) {
  const { user } = useSession();
  const path = "/chats/" + encodeURIComponent(id);
  const chat = useData<Chat>(path);
  const history = useData<MessagePage>(chat.data ? path + "/messages" : "", 4000);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [hasOlder, setHasOlder] = useState<boolean>();
  const [text, setText] = useState("");
  const [attachment, setAttachment] = useState<File>();
  const [preview, setPreview] = useState<NonNullable<ChatMessage["attachment"]>>();
  const [sending, setSending] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [error, setError] = useState("");
  const attachmentInput = useRef<HTMLInputElement>(null);
  const draft = useRef<{ text: string; file?: File; requestId: string } | null>(null);
  const readThrough = useRef(0);
  const historyList = useRef<HTMLOListElement>(null);
  const followHistory = useRef(true);
  const previousHeight = useRef<number>(null);
  const latest = history.data?.messages;
  const unreadSeq = latest?.findLast((message) => message.senderId !== user?.id && !message.readAt)?.seq;

  useEffect(() => {
    if (!history.data) return;
    const { messages: incoming, hasMore } = history.data;
    setMessages((previous) => mergeMessages(previous, incoming));
    setHasOlder((previous) => previous ?? hasMore);
  }, [history.data]);

  useLayoutEffect(() => {
    const list = historyList.current;
    if (!list) return;
    if (previousHeight.current !== null) {
      list.scrollTop += list.scrollHeight - previousHeight.current;
      previousHeight.current = null;
    } else if (followHistory.current) {
      list.scrollTop = list.scrollHeight;
    }
  }, [messages]);

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
      previousHeight.current = historyList.current?.scrollHeight ?? null;
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
    if ((!message && !attachment) || sending) return;
    if (draft.current?.text !== message || draft.current.file !== attachment) {
      draft.current = { text: message, file: attachment, requestId: crypto.randomUUID() };
    }
    const { requestId } = draft.current;
    setSending(true);
    setError("");
    try {
      let saved: ChatMessage;
      if (attachment) {
        const form = new FormData();
        form.append("file", attachment);
        form.append("requestId", requestId);
        if (message) form.append("text", message);
        saved = await api<ChatMessage>(path + "/messages/attachments", { method: "POST", form });
      } else {
        saved = await api<ChatMessage>(path + "/messages", { method: "POST", json: { text: message, requestId } });
      }
      followHistory.current = true;
      setMessages((previous) => mergeMessages(previous, [saved]));
      setText("");
      setAttachment(undefined);
      draft.current = null;
      history.reload();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setSending(false);
    }
  }

  function chooseAttachment(file?: File) {
    if (!file) return;
    if (!file.size || file.size > 5 * 1024 * 1024) {
      setError("Выберите непустой файл размером до 5 МБ.");
      return;
    }
    setError("");
    setAttachment(file);
  }

  return <section className="panel conversation">
    <p className="breadcrumbs"><Link href="/messages">К диалогам</Link></p>
    <RequestStatus loading={chat.loading} error={chat.error} retry={chat.reload} />
    {chat.data && <>
      <header className="conversation-heading">
        <span className="chat-avatar" aria-hidden="true" />
        <div className="conversation-participant"><h1>{chat.data.counterpart.name}</h1>
          <Link href={"/items/" + encodeURIComponent(chat.data.item.id)}>{chat.data.item.title}</Link>
        </div>
        <details className="conversation-menu">
          <summary aria-label="Действия диалога"><img src="/design/chat-menu.svg" alt="" /></summary>
          <Link href={"/profiles/" + encodeURIComponent(chat.data.counterpart.id)}>Открыть профиль</Link>
        </details>
      </header>
      <ChatTransfer chatId={id} itemId={chat.data.item.id} />
      <RequestStatus loading={history.loading && !messages.length} error={history.error} retry={history.reload} />
      {hasOlder && <button type="button" className="secondary" disabled={loadingOlder} onClick={loadOlder}>Более ранние сообщения</button>}
      {!history.loading && !history.error && !messages.length && <p>Сообщений пока нет.</p>}
      <ol className="activity-list chat-history" aria-label="История сообщений" ref={historyList} onScroll={(event) => {
        const list = event.currentTarget;
        followHistory.current = list.scrollHeight - list.scrollTop - list.clientHeight < 70;
      }}>
        {messages.map((message, index) => {
          const date = new Date(message.sentAt);
          const newDay = index === 0 || new Date(messages[index - 1].sentAt).toDateString() !== date.toDateString();
          const own = message.senderId === user?.id;
          return <li key={message.id} className={own ? "own-message" : ""}>
            {newDay && <div className="chat-day">{date.toDateString() === new Date().toDateString() && "Сегодня, "}{dayFormat.format(date)}</div>}
            <div className="chat-message-row">
              <span className="chat-avatar" aria-hidden="true" />
              <div className="chat-bubble">
                {message.text && <p>{message.text}</p>}
                {message.attachment && <ChatFile file={message.attachment} onPreview={setPreview} />}
                <small><time dateTime={message.sentAt}>{timeFormat.format(date)}</time>
                  {own && <span className={message.readAt ? "chat-receipt is-read" : "chat-receipt"} role="img" aria-label={message.readAt ? "Прочитано" : "Отправлено"} title={message.readAt ? "Прочитано" : "Отправлено"}>
                    <svg viewBox="0 0 20 14" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                      <path d={message.readAt ? "M1.5 7.5 5 11l7.5-8M9 9.5l1.5 1.5L18 3" : "M6 7.5 9.5 11 17 3"} />
                    </svg>
                  </span>}
                </small>
              </div>
            </div>
          </li>;
        })}
      </ol>
      {preview && <ChatAttachmentPreview file={preview} onClose={() => setPreview(undefined)} />}
      {attachment && <div className="chat-file-preview">
        <span>{attachment.name}</span>
        <button type="button" className="secondary" disabled={sending} onClick={() => setAttachment(undefined)} aria-label="Убрать вложение">×</button>
      </div>}
      <form className="chat-composer" onSubmit={send}>
        <input ref={attachmentInput} type="file" hidden onChange={(event) => {
          chooseAttachment(event.currentTarget.files?.[0]);
          event.currentTarget.value = "";
        }} />
        <button className="chat-attach" type="button" disabled={sending} aria-label="Прикрепить файл" title="Прикрепить файл до 5 МБ"
          onClick={() => attachmentInput.current?.click()}>
          <img src="/design/chat-attach.svg" alt="" />
        </button>
        <textarea aria-label="Сообщение" placeholder="Написать сообщение..." rows={1} value={text} maxLength={2000} disabled={sending} onChange={(event) => setText(event.target.value)} />
        <button className="chat-send" aria-label={sending ? "Отправляем…" : "Отправить"} disabled={sending || (!text.trim() && !attachment)}>
          <img src="/design/chat-send.svg" alt="" />
        </button>
      </form>
      <RequestStatus error={error} />
    </>}
  </section>;
}

function ChatFile({ file, onPreview }: { file: NonNullable<ChatMessage["attachment"]>; onPreview: (file: NonNullable<ChatMessage["attachment"]>) => void }) {
  const [imageFailed, setImageFailed] = useState(false);
  const previewType = attachmentPreviewType(file.name);

  if (previewType?.startsWith("image/")) {
    return <button type="button" className="chat-image" aria-label="Открыть изображение" onClick={() => onPreview(file)}>
      {imageFailed ? <span>Изображение недоступно</span>
        : <img src={file.url} alt="" loading="lazy" onError={() => setImageFailed(true)} />}
    </button>;
  }

  const content = <><img src="/design/chat-attach.svg" alt="" /><span>{file.name}</span></>;
  return previewType
    ? <button type="button" className="chat-file" aria-label={"Посмотреть " + file.name} onClick={() => onPreview(file)}>{content}</button>
    : <a className="chat-file" href={file.url} download={file.name}>{content}</a>;
}
