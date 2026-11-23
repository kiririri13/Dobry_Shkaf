"use client";

import Link from "next/link";
import { useState } from "react";
import { FavoriteButton, favoritesPath } from "@/components/FavoriteButton";
import RequestStatus from "@/components/RequestStatus";
import { PrivatePage } from "@/components/Session";
import { api, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Favorites } from "@/lib/types";

export default function FavoritesPage() {
  return <PrivatePage><FavoriteList /></PrivatePage>;
}

function FavoriteList() {
  const favorites = useData<Favorites>(favoritesPath);
  const [type, setType] = useState<"ITEMS" | "POINTS">("ITEMS");
  const [removed, setRemoved] = useState<string[]>([]);
  const [clearing, setClearing] = useState(false);
  const [error, setError] = useState("");
  const cards = type === "ITEMS"
    ? favorites.data?.items.map((item) => ({ id: item.id, title: item.title, href: "/items/" + item.id, photo: item.photos[0]?.url, district: item.district?.name, size: item.size }))
    : favorites.data?.points.map((point) => ({ id: point.id, title: point.name, href: "/points/" + point.id, address: point.address }));
  const visible = cards?.filter((card) => !removed.includes(type + card.id));

  async function clear() {
    if (!visible?.length || !window.confirm("Убрать все сохранённые записи из этого раздела?")) return;
    setClearing(true);
    setError("");
    const results = await Promise.allSettled(visible.map(async ({ id }) => {
      await api(favoritesPath + "/" + type + "/" + encodeURIComponent(id), { method: "DELETE" });
      return type + id;
    }));
    setRemoved((previous) => [...previous, ...results.flatMap((result) => result.status === "fulfilled" ? [result.value] : [])]);
    const failure = results.find((result) => result.status === "rejected");
    if (failure) setError(errorMessage(failure.reason));
    setClearing(false);
  }

  return <section className="favorites">
    <div className="section-head">
      <div><h1>Избранное</h1><p>Ваши сохранённые {type === "ITEMS" ? "объявления" : "пункты приёма"}</p></div>
      <button className="secondary favorite-clear" type="button" onClick={clear} disabled={!visible?.length || clearing}>
        <img src="/design/favorite-trash.png" alt="" />Убрать всё
      </button>
    </div>
    {Boolean(favorites.data?.points.length) && <div className="profile-tabs">
      <button type="button" aria-pressed={type === "ITEMS"} disabled={clearing} onClick={() => setType("ITEMS")}>Вещи</button>
      <button type="button" aria-pressed={type === "POINTS"} disabled={clearing} onClick={() => setType("POINTS")}>Пункты приёма</button>
    </div>}
    <RequestStatus loading={favorites.loading} error={favorites.error} retry={favorites.reload} />
    <RequestStatus error={error} />
    {visible?.length === 0 && <p className="activity-heading">Здесь пока ничего нет.</p>}
    <div className="cards favorite-cards">
      {visible?.map((card) => <article className="card" key={type + card.id}>
        {"photo" in card && <Link className="card-image" href={card.href}>
          {card.photo ? <img src={card.photo} alt={card.title} /> : <span>Без фото</span>}
        </Link>}
        <div className="card-body">
          <h2><Link href={card.href}>{card.title}</Link></h2>
          {"address" in card ? <p>{card.address}</p> : <>
            {card.district && <p>{card.district}</p>}
            {card.size && <p>Размер: {card.size}</p>}
          </>}
          <FavoriteButton id={card.id} type={type} compact saved disabled={clearing}
            onChange={() => setRemoved((previous) => [...previous, type + card.id])} />
        </div>
      </article>)}
    </div>
  </section>;
}
