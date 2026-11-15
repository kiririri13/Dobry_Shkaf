"use client";

import Link from "next/link";
import { useState } from "react";
import { PrivatePage, useSession } from "@/components/Session";
import RequestStatus from "@/components/RequestStatus";
import ItemList from "@/components/ItemList";
import { api, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { ItemPage, Transfer } from "@/lib/types";
import { transferStatuses } from "@/components/Transfers";

const sections = ["Мои объявления", "История", "Отзывы", "Вклад в экологию"];

export default function ProfilePage() {
  return <PrivatePage><Profile /></PrivatePage>;
}

function Profile() {
  const { user, setUser } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [section, setSection] = useState(0);
  const listings = useData<ItemPage>("/items?mine=true");
  const transfers = useData<Transfer[]>("/transfers");
  const given = transfers.data?.filter((transfer) => transfer.myRole === "GIVER" && transfer.status === "COMPLETED").length;
  const stats = [
    ["profile-listings", "Объявления", listings.data?.totalElements],
    ["profile-given", "Отдано", given],
    ["profile-rating", "Рейтинг", undefined],
    ["profile-reviews", "Отзывы", undefined],
  ] as const;

  async function logout() {
    setBusy(true);
    setError("");
    try {
      await api("/auth/logout", { method: "POST" });
      setUser(null);
      window.location.replace("/");
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <>
    <p className="breadcrumbs"><Link href="/">Главная</Link> / Профиль</p>
    <section className="profile">
      <div className="profile-cover">
        <button type="button" className="secondary profile-edit" disabled title="Редактирование пока недоступно">
          <img src="/design/profile-edit.svg" alt="" />Редактировать профиль
        </button>
      </div>
      <div className="profile-info">
        <div className="profile-avatar">
          <span aria-hidden="true">{user?.name.trim().charAt(0).toUpperCase()}</span>
          <button type="button" className="profile-avatar-edit" disabled aria-label="Изменить фото профиля" title="Редактирование пока недоступно">
            <img src="/design/profile-avatar-edit.svg" alt="" />
          </button>
        </div>
        <div className="profile-name"><h1>{user?.name}</h1></div>
        <button type="button" className="secondary profile-logout" onClick={logout} disabled={busy}>{busy ? "Выходим…" : "Выйти"}</button>
      </div>
      <RequestStatus error={error} />
      <dl className="profile-stats" aria-label="Статистика профиля">
        {stats.map(([icon, label, value]) => <div key={label}>
          <img src={"/design/" + icon + ".svg"} alt="" />
          <dt>{label}</dt>
          <dd>{value ?? <span aria-label="Нет данных">—</span>}</dd>
        </div>)}
      </dl>
      <RequestStatus error={listings.error || transfers.error} retry={() => { listings.reload(); transfers.reload(); }} />
    </section>
    <nav className="profile-tabs" aria-label="Разделы профиля">
      {sections.map((label, index) => <button type="button" key={label} aria-pressed={section === index} onClick={() => setSection(index)}>{label}</button>)}
    </nav>
    {section === 0 && <section className="profile-items" aria-label="Мои объявления">
      <div className="profile-sort">Сортировка <img src="/design/profile-sort.svg" alt="" /> Сначала новые</div>
      <ItemList mine compact />
    </section>}
    {section === 1 && <section aria-label="История">
      <p className="profile-history-link"><Link href="/profile/transfers">Все заявки и передачи</Link></p>
      <RequestStatus loading={transfers.loading} error={transfers.error} retry={transfers.reload} />
      {transfers.data?.length === 0 && <p>Заявок и передач пока нет.</p>}
      <div className="cards profile-cards">
        {transfers.data?.map((transfer) => <article className="card" key={transfer.id}>
          <Link className="card-image" href={"/profile/transfers/" + transfer.id}>
            {transfer.item.photoUrl ? <img src={transfer.item.photoUrl} alt={transfer.item.title} /> : <span>Без фото</span>}
            <span className="badge">{transferStatuses[transfer.status]}</span>
          </Link>
          <div className="card-body">
            <h2><Link href={"/profile/transfers/" + transfer.id}>{transfer.item.title}</Link></h2>
            <p>{transfer.myRole === "GIVER" ? "Отдаю" : "Получаю"}</p>
          </div>
        </article>)}
      </div>
    </section>}
    {section === 2 && <section className="profile-reviews" aria-label="Отзывы">
      <div className="panel"><h2>Общая оценка</h2><p className="profile-rating-value">—</p></div>
      <p className="panel">Отзывы пока недоступны.</p>
    </section>}
    {section === 3 && <section className="profile-reviews" aria-label="Вклад в экологию">
      <div className="panel"><h2>Отдано вещей</h2><p className="profile-rating-value">{given ?? "—"}</p></div>
      <p className="panel">Данных о сокращении CO₂ пока нет.</p>
    </section>}
  </>;
}
