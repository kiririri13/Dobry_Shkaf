"use client";

import Link from "next/link";
import { useState } from "react";
import { useData } from "@/lib/useData";
import type { Condition, Item } from "@/lib/types";
import { itemStatuses } from "./ItemList";
import RequestStatus from "./RequestStatus";

export default function ItemDetails({ id }: { id: string }) {
  const path = "/items/" + encodeURIComponent(id);
  const item = useData<Item>(path);
  const conditions = useData<Condition[]>("/item-conditions");
  const [selectedPhoto, setSelectedPhoto] = useState("");
  const data = item.data;
  if (!data) return <RequestStatus loading={item.loading} error={item.error} retry={item.reload} />;
  const photo = data.photos.find((entry) => entry.id === selectedPhoto) || data.photos[0];

  return <article className="item-detail">
    <p className="breadcrumbs"><Link href="/">Главная</Link> / <Link href="/items">Каталог</Link> / Вещь</p>
    <div className="item-media">
      <div className="item-gallery">
        {photo ? <a className="item-photo" href={photo.url}><img src={photo.url} alt={data.title} />
          {data.photos.length > 1 && <span>{data.photos.indexOf(photo) + 1}/{data.photos.length}</span>}
        </a> : <div className="item-photo">Без фото</div>}
        {data.photos.length > 1 && <div className="item-thumbs">
          {data.photos.map((entry, index) => <button key={entry.id} type="button" onClick={() => setSelectedPhoto(entry.id)}
            aria-pressed={entry.id === photo?.id} aria-label={`Фото ${index + 1}`}><img src={entry.url} alt="" /></button>)}
        </div>}
      </div>
      <div className="panel item-owner">
        <span aria-hidden="true">{data.owner.name.trim().charAt(0).toUpperCase()}</span>
        <div><p>Владелец</p><h2>{data.owner.name}</h2></div>
      </div>
    </div>
    <section className="item-info">
      <h1>{data.title}</h1>
      <span className={data.status === "RESERVED" ? "badge reserved" : "badge"}>
        {data.status === "PUBLISHED" ? "Можно забрать" : itemStatuses[data.status]}
      </span>
      <dl>
        {data.category && <><dt>Категория</dt><dd>{data.category.name}</dd></>}
        {data.size && <><dt>Размер</dt><dd>{data.size}</dd></>}
        {data.condition && <><dt>Состояние</dt><dd>{conditions.data?.find((entry) => entry.code === data.condition)?.label || data.condition}</dd></>}
        {data.district && <><dt>Район</dt><dd>{data.district.name}</dd></>}
      </dl>
      {data.description && <><h2>Описание</h2><p className="description">{data.description}</p></>}
    </section>
  </article>;
}
