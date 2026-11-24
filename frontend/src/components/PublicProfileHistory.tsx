"use client";

import { useState } from "react";
import { api, errorMessage } from "@/lib/api";
import { deliveryMethods } from "@/lib/deliveryMethods";
import type { DeliveryMethod } from "@/lib/types";
import { useData } from "@/lib/useData";
import RequestStatus from "./RequestStatus";

type HistoryEntry = {
  id: string;
  itemId: string;
  title: string;
  deliveryMethod: DeliveryMethod;
  completedAt: string;
  role: "GIVER" | "RECEIVER";
  photoUrl?: string | null;
  size?: string | null;
};

type HistoryPage = { items: HistoryEntry[]; page: number; hasMore: boolean };
const dateFormat = new Intl.DateTimeFormat("ru-RU", { dateStyle: "medium" });

export default function PublicProfileHistory({ path }: { path: string }) {
  const first = useData<HistoryPage>(path);
  const [loaded, setLoaded] = useState<HistoryPage>();
  const [order, setOrder] = useState("recent");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const history = loaded ?? first.data;
  const entries = history?.items ?? [];

  async function loadHistory(all = false) {
    if (!history || busy) return;
    let result = history;
    setBusy(true);
    setError("");
    try {
      while (result.hasMore) {
        const next = await api<HistoryPage>(`${path}?page=${result.page + 1}`);
        const existing = new Set(result.items.map(({ id }) => id));
        result = { ...next, items: [...result.items, ...next.items.filter(({ id }) => !existing.has(id))] };
        if (!all) break;
      }
      if (all) setOrder("oldest");
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setLoaded(result);
      setBusy(false);
    }
  }

  return <section className="public-profile-history" aria-label="История передач">
    <RequestStatus loading={first.loading} error={first.error} retry={first.reload} />
    <RequestStatus error={error} />
    {history && entries.length === 0 && <p>Завершенных передач пока нет.</p>}
    {entries.length > 0 && <>
      <label className="profile-sort">Сортировка
        <select value={order} disabled={busy} onChange={(event) => {
          if (event.target.value === "oldest") void loadHistory(true);
          else setOrder("recent");
        }}>
          <option value="recent">Сначала новые</option>
          <option value="oldest">Сначала старые</option>
        </select>
      </label>
      <div className="cards profile-cards profile-history-cards">
        {(order === "oldest" ? [...entries].reverse() : entries).map((entry) => <article className="card" key={entry.id} data-status="COMPLETED">
          <div className="card-image">
            {entry.photoUrl && <img src={entry.photoUrl} alt="" />}
            <span className="badge">{deliveryMethods[entry.deliveryMethod].label}</span>
          </div>
          <div className="card-body">
            <h2>{entry.title}</h2>
            {entry.size && <p>Размер: {entry.size}</p>}
            <p>{entry.role === "GIVER" ? "Передано" : "Получено"}</p>
            <p><time dateTime={entry.completedAt}>{dateFormat.format(new Date(entry.completedAt))}</time></p>
          </div>
        </article>)}
      </div>
      {busy && <RequestStatus loading />}
      {history?.hasMore && <button type="button" className="secondary public-history-more" disabled={busy} onClick={() => loadHistory()}>Показать еще</button>}
    </>}
  </section>;
}
