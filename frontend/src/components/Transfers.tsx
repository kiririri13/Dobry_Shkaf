"use client";

import Link from "next/link";
import { useState } from "react";
import { api, ApiError, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Transfer, TransferAction, TransferEvent, TransferStatus } from "@/lib/types";
import RequestStatus from "./RequestStatus";

export const transferStatuses: Record<TransferStatus, string> = {
  REQUESTED: "Заявка отправлена",
  RESERVED: "Вещь зарезервирована",
  AWAITING_CONFIRMATION: "Ожидает второго подтверждения",
  CANCELLATION_REQUESTED: "Запрошена отмена",
  COMPLETED: "Вещь передана",
  REJECTED: "Заявка отклонена",
  CANCELLED: "Передача отменена",
};

const commands: Record<TransferAction, string> = {
  SELECT: "select",
  CONFIRM: "confirm",
  CANCEL: "cancel",
  ACCEPT_CANCELLATION: "cancellation/accept",
  DECLINE_CANCELLATION: "cancellation/decline",
};

function actionLabel(action: TransferAction, transfer: Transfer) {
  if (action === "SELECT") return "Отдать этому человеку";
  if (action === "CONFIRM") return transfer.myRole === "GIVER" ? "Вещь передана" : "Вещь получена";
  if (action === "ACCEPT_CANCELLATION") return "Согласиться на отмену";
  if (action === "DECLINE_CANCELLATION") return "Продолжить передачу";
  if (transfer.status === "REQUESTED") return transfer.myRole === "GIVER" ? "Отклонить" : "Отозвать заявку";
  return transfer.status === "AWAITING_CONFIRMATION" ? "Попросить отмену" : "Отменить передачу";
}

export function TransferList() {
  const transfers = useData<Transfer[]>("/transfers", 5000);
  return <>
    <p className="breadcrumbs"><Link href="/profile">Профиль</Link> / Передачи</p>
    <h1>Мои передачи</h1>
    <RequestStatus loading={transfers.loading} error={transfers.error} retry={transfers.reload} />
    {transfers.data?.length === 0 && <p>Заявок и передач пока нет.</p>}
    <ul className="activity-list">
      {transfers.data?.map((transfer) => <li key={transfer.id}>
        <Link className="panel activity-link" href={"/profile/transfers/" + transfer.id}>
          <strong>{transfer.item.title}</strong>
          <p>{transfer.myRole === "GIVER" ? "Отдаю" : "Получаю"} · {transferStatuses[transfer.status]}</p>
        </Link>
      </li>)}
    </ul>
  </>;
}

export function TransferDetails({ id }: { id: string }) {
  const path = "/transfers/" + encodeURIComponent(id);
  const transfer = useData<Transfer>(path, 5000);
  const events = useData<TransferEvent[]>(transfer.data ? path + "/events" : "", 5000);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function change(action: TransferAction) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await api(path + "/" + commands[action], { method: "POST" });
      transfer.reload();
      events.reload();
    } catch (failure) {
      setError(errorMessage(failure));
      if (failure instanceof ApiError && failure.status === 409) transfer.reload();
    } finally {
      setBusy(false);
    }
  }

  const data = transfer.data;
  return <section className="panel narrow">
    <p className="breadcrumbs"><Link href="/profile/transfers">Мои передачи</Link></p>
    <RequestStatus loading={transfer.loading} error={transfer.error} retry={transfer.reload} />
    <RequestStatus error={error} />
    {data && <>
      <h1>{data.item.title}</h1>
      <p><span className="badge">{transferStatuses[data.status]}</span></p>
      <p>Отдаёт: {data.giver.name}. Получает: {data.receiver.name}.</p>
      <p><Link href={"/messages/" + data.chatId}>Открыть диалог</Link></p>
      <div className="actions">
        {data.availableActions.map((action) => <button key={action} disabled={busy} onClick={() => change(action)}>{actionLabel(action, data)}</button>)}
      </div>
      <h2 className="activity-heading">История передачи</h2>
      <RequestStatus loading={events.loading} error={events.error} retry={events.reload} />
      {events.data?.length === 0 && <p>Изменений пока нет.</p>}
      <ol>
        {events.data?.map((event) => <li key={event.id}>
          {transferStatuses[event.toStatus]} · {event.actorId === data.giver.id ? data.giver.name : data.receiver.name} ·{" "}
          <time dateTime={event.createdAt}>{new Date(event.createdAt).toLocaleString("ru-RU")}</time>
        </li>)}
      </ol>
    </>}
  </section>;
}
