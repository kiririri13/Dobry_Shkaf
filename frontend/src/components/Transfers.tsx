"use client";

import Link from "next/link";
import { useState } from "react";
import { api, ApiError, errorMessage, query } from "@/lib/api";
import { deliveryMethods } from "@/lib/deliveryMethods";
import { useData } from "@/lib/useData";
import type { Transfer, TransferAction, TransferEvent, TransferStatus } from "@/lib/types";
import RequestStatus from "./RequestStatus";

const transferStatuses: Record<TransferStatus, string> = {
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
  if (action === "SELECT") return transfer.item.deliveryMethod === "EXCHANGE" ? "Согласиться на обмен" : "Выбрать получателя";
  if (action === "CONFIRM") return transfer.myRole === "GIVER" ? "Я отдал вещь" : "Я забрал вещь";
  if (action === "ACCEPT_CANCELLATION") return "Согласиться на отмену";
  if (action === "DECLINE_CANCELLATION") return "Продолжить передачу";
  if (transfer.status === "REQUESTED") return transfer.myRole === "GIVER" ? "Отклонить" : "Отозвать заявку";
  return transfer.status === "AWAITING_CONFIRMATION" ? "Попросить отмену" : "Отменить передачу";
}

export function transferStatusLabel(transfer: Transfer) {
  if (transfer.status === "AWAITING_CONFIRMATION") {
    return transfer.receiverConfirmed ? "Ждём подтверждения владельца" : "Ждём подтверждения получателя";
  }
  if (transfer.status === "COMPLETED" && transfer.item.deliveryMethod === "EXCHANGE") return "Обмен завершён";
  return transferStatuses[transfer.status];
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
          <p>{transfer.myRole === "GIVER" ? "Отдаю" : "Получаю"} · {transferStatusLabel(transfer)}</p>
        </Link>
      </li>)}
    </ul>
  </>;
}

function TransferProgress({ transfer, onUpdate }: { transfer?: Transfer; onUpdate: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function change(action: TransferAction) {
    if (!transfer || busy) return;
    setBusy(true);
    setError("");
    try {
      await api("/transfers/" + encodeURIComponent(transfer.id) + "/" + commands[action], { method: "POST" });
      onUpdate();
    } catch (failure) {
      setError(errorMessage(failure));
      if (failure instanceof ApiError && failure.status === 409) onUpdate();
    } finally {
      setBusy(false);
    }
  }

  return <>
    {transfer && <>
      <div className="actions" role="status">
        <span className="badge">{deliveryMethods[transfer.item.deliveryMethod ?? "FREE"].label}</span>
        <span>{transferStatusLabel(transfer)}</span>
      </div>
      {transfer.status === "RESERVED" && <p>{transfer.myRole === "RECEIVER"
        ? "После встречи отметьте, что забрали вещь."
        : "Получатель первым отмечает, что забрал вещь."}</p>}
      {transfer.availableActions.length > 0 && <div className="actions">
        {transfer.availableActions.map((action) => <button key={action} type="button" disabled={busy}
          className={action === "SELECT" || action === "CONFIRM" ? undefined : "secondary"}
          onClick={() => change(action)}>{actionLabel(action, transfer)}</button>)}
      </div>}
    </>}
    <RequestStatus error={error} />
  </>;
}

export function ChatTransfer({ chatId, itemId }: { chatId: string; itemId: string }) {
  const transfers = useData<Transfer[]>("/transfers" + query({ itemId }), 5000);
  const transfer = transfers.data?.find((entry) => entry.chatId === chatId);

  return <div className="chat-transfer">
    <RequestStatus error={transfers.error} retry={transfers.reload} />
    <TransferProgress transfer={transfer} onUpdate={transfers.reload} />
    {transfer && <Link href={"/profile/transfers/" + encodeURIComponent(transfer.id)}>Детали передачи</Link>}
  </div>;
}

export function TransferDetails({ id }: { id: string }) {
  const path = "/transfers/" + encodeURIComponent(id);
  const transfer = useData<Transfer>(path, 5000);
  const events = useData<TransferEvent[]>(transfer.data ? path + "/events" : "", 5000);
  const data = transfer.data;
  return <section className="panel narrow">
    <p className="breadcrumbs"><Link href="/profile/transfers">Мои передачи</Link></p>
    <RequestStatus loading={transfer.loading} error={transfer.error} retry={transfer.reload} />
    {data && <>
      <h1>{data.item.title}</h1>
      <p>Отдает: {data.giver.name}. Получает: {data.receiver.name}.</p>
      <p><Link href={"/messages/" + data.chatId}>Открыть диалог</Link></p>
    </>}
    <TransferProgress transfer={data} onUpdate={() => { transfer.reload(); events.reload(); }} />
    {data && <>
      {data.status === "COMPLETED" && <p><Link className="button secondary" href={`/profile/transfers/${id}/review`}>Оставить отзыв</Link></p>}
      <h2 className="activity-heading">История передачи</h2>
      <RequestStatus loading={events.loading} error={events.error} retry={events.reload} />
      {events.data?.length === 0 && <p>Изменений пока нет.</p>}
      <ol>
        {events.data?.map((event) => <li key={event.id}>
          {event.command === "CONFIRM" ? event.actorId === data.giver.id ? "Владелец подтвердил передачу" : "Получатель подтвердил получение"
            : transferStatuses[event.toStatus]} · {event.actorId === data.giver.id ? data.giver.name : data.receiver.name} ·{" "}
          <time dateTime={event.createdAt}>{new Date(event.createdAt).toLocaleString("ru-RU")}</time>
        </li>)}
      </ol>
    </>}
  </section>;
}
