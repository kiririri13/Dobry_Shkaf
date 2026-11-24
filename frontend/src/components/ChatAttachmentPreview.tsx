"use client";

import { useEffect, useRef, useState } from "react";
import type { ChatMessage } from "@/lib/types";

type Attachment = NonNullable<ChatMessage["attachment"]>;

const imageTypes: Record<string, string> = {
  jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png", gif: "image/gif",
  webp: "image/webp", avif: "image/avif", bmp: "image/bmp", ico: "image/x-icon",
};

export function attachmentPreviewType(name: string) {
  const extension = name.toLowerCase().split(".").pop() ?? "";
  if (extension === "pdf") return "application/pdf";
  if (["txt", "csv", "log", "md", "json"].includes(extension)) return "text/plain";
  return Object.hasOwn(imageTypes, extension) ? imageTypes[extension] : undefined;
}

export default function ChatAttachmentPreview({ file, onClose }: { file: Attachment; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [source, setSource] = useState("");
  const [plainText, setPlainText] = useState<string>();
  const [failed, setFailed] = useState(false);
  const type = attachmentPreviewType(file.name);

  useEffect(() => {
    if (dialog.current && !dialog.current.open) dialog.current.showModal();
    const request = new AbortController();
    let objectUrl = "";

    async function load() {
      const url = new URL(file.url, window.location.origin);
      if (url.origin !== window.location.origin) throw new Error();
      const response = await fetch(url, { signal: request.signal, cache: "no-store" });
      if (!response.ok) throw new Error();
      const blob = await response.blob();
      if (type === "text/plain") {
        const content = await blob.text();
        if (!request.signal.aborted) setPlainText(content);
        return;
      }
      if (type === "application/pdf" && await blob.slice(0, 5).text() !== "%PDF-") throw new Error();
      if (request.signal.aborted) return;
      objectUrl = URL.createObjectURL(blob.slice(0, blob.size, type));
      setSource(objectUrl);
    }

    void load().catch(() => { if (!request.signal.aborted) setFailed(true); });
    return () => {
      request.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [file.url, type]);

  return <dialog ref={dialog} className="chat-attachment-dialog" aria-labelledby="chat-attachment-title" onClose={onClose}>
    <header>
      <h2 id="chat-attachment-title">{file.name}</h2>
      <a className="chat-attachment-download" href={file.url} download={file.name}>Скачать</a>
      <button type="button" aria-label="Закрыть предпросмотр" autoFocus onClick={() => dialog.current?.close()}>×</button>
    </header>
    {failed ? <p role="status">Не удалось открыть предпросмотр.</p>
      : plainText !== undefined ? <pre>{plainText}</pre>
      : source && type === "application/pdf" ? <iframe src={source} title={file.name} />
      : source ? <img src={source} alt={file.name} onError={() => setFailed(true)} />
      : <p role="status">Загрузка файла…</p>}
  </dialog>;
}
