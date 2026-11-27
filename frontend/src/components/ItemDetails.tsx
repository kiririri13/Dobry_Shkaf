"use client";

import Link from "next/link";
import { useRef, useState, type FormEvent } from "react";
import { api, errorMessage, query } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Condition, Item, ItemPage, Profile } from "@/lib/types";
import { deliveryMethods } from "@/lib/deliveryMethods";
import { useSession } from "./Session";
import { itemStatuses } from "./ItemList";
import RequestStatus from "./RequestStatus";
import ItemContact from "./ItemContact";
import { FavoriteControl } from "./FavoriteButton";

const publicationAge = new Intl.NumberFormat("ru-RU", { style: "unit", unit: "day", unitDisplay: "long" });

export default function ItemDetails({ id }: { id: string }) {
  const { user } = useSession();
  const path = "/items/" + encodeURIComponent(id);
  const item = useData<Item>(path);
  const conditions = useData<Condition[]>("/item-conditions");
  const [updated, setUpdated] = useState<Item>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selectedPhoto, setSelectedPhoto] = useState("");
  const data = updated?.id === id ? updated : item.data;
  const owner = useData<Profile>(data ? "/profiles/" + encodeURIComponent(data.owner.id) : "").data;
  const categoryPath = data?.category ? "/items" + query({ categoryId: data.category.id }) : "";
  const related = useData<ItemPage>(categoryPath);
  const complaint = useRef<HTMLDialogElement>(null);
  const [reportBusy, setReportBusy] = useState(false);
  const [reportError, setReportError] = useState("");
  const [reported, setReported] = useState(false);

  async function change(action: "publish" | "close") {
    if (action === "close" && !window.confirm("Закрыть объявление?")) return;
    setError("");
    setNotice("");
    setBusy(true);
    try {
      setUpdated(await api<Item>(path + "/" + action, { method: "POST" }));
      setNotice(action === "publish" ? "Объявление отправлено на проверку." : "Объявление закрыто.");
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  async function report(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const reason = String(new FormData(event.currentTarget).get("reason")).trim();
    if (!reason || reportBusy) return;
    setReportBusy(true);
    setReportError("");
    try {
      await api("/complaints", { method: "POST", json: { targetType: "ITEM", targetId: id, reason } });
      complaint.current?.close();
      setReported(true);
    } catch (failure) {
      setReportError(errorMessage(failure));
    } finally {
      setReportBusy(false);
    }
  }

  if (!data) return <RequestStatus loading={item.loading} error={item.error} retry={item.reload} />;
  const canManage = user && (data.owner.id === user.id || user.role === "ADMIN");
  const photoIndex = Math.max(data.photos.findIndex((entry) => entry.id === selectedPhoto), 0);
  const photo = data.photos[photoIndex];
  const condition = conditions.data?.find((entry) => entry.code === data.condition)?.label;
  const published = data.publishedAt ? new Date(data.publishedAt) : null;
  const daysPublished = published ? Math.max(0, Math.floor((Date.now() - published.getTime()) / 86_400_000)) : 0;
  const similar = related.data?.items.filter((entry) => entry.id !== id).slice(0, 3) ?? [];
  const profile = user?.id === data.owner.id ? "/profile" : "/profiles/" + encodeURIComponent(data.owner.id);
  const hidden = data.moderationStatus === "HIDDEN";
  const pending = ["PUBLISHED", "RESERVED"].includes(data.status) && data.moderationStatus === "PENDING";
  const visible = data.moderationStatus === "REVIEWED" && ["PUBLISHED", "RESERVED"].includes(data.status);
  const canReport = user?.id !== data.owner.id && visible;
  function reportAction(className: string) {
    if (!canReport) return null;
    if (user === null) return <Link className={className} href={"/login?next=" + encodeURIComponent(path)}>Пожаловаться</Link>;
    return <button className={className} type="button" disabled={reported}
      onClick={() => complaint.current?.showModal()}>Пожаловаться</button>;
  }

  return <article className="item-detail">
    <p className="breadcrumbs"><Link href="/">Главная</Link> / <Link href="/items">Каталог</Link> / Вещь</p>
    <div className="item-left">
      <div className="item-media">
        <div className="item-gallery">
          <div className="item-photo">
            {photo ? <a href={photo.url}><img src={photo.url} alt={data.title} /></a> : "Без фото"}
            {data.photos.length > 1 && <>
              <span className="item-photo-count">{photoIndex + 1}/{data.photos.length}</span>
              <div className="item-photo-steps" aria-label="Фотографии">
                {data.photos.map((entry, index) => <button key={entry.id} type="button" onClick={() => setSelectedPhoto(entry.id)}
                  aria-label={`Фото ${index + 1}`} aria-pressed={entry.id === photo?.id} />)}
              </div>
            </>}
          </div>
          {data.photos.length > 1 && <div className="item-thumbs" aria-label="Миниатюры фотографий">
            <button className="item-gallery-arrow previous" type="button" aria-label="Предыдущее фото" disabled={photoIndex === 0}
              onClick={() => setSelectedPhoto(data.photos[photoIndex - 1].id)}><img src="/design/gallery-arrow.svg" alt="" /></button>
            {data.photos.slice(Math.min(photoIndex, Math.max(0, data.photos.length - 3))).slice(0, 3)
              .map((entry) => <button className="item-thumbnail" key={entry.id} type="button" onClick={() => setSelectedPhoto(entry.id)}
                aria-pressed={entry.id === photo?.id} aria-label={`Открыть фото ${data.photos.indexOf(entry) + 1}`}><img src={entry.url} alt="" /></button>)}
            <button className="item-gallery-arrow next" type="button" aria-label="Следующее фото" disabled={photoIndex === data.photos.length - 1}
              onClick={() => setSelectedPhoto(data.photos[photoIndex + 1].id)}><img src="/design/gallery-arrow.svg" alt="" /></button>
          </div>}
        </div>
      </div>
      <aside className="panel item-owner">
        <div className="item-owner-name">
          <span className="item-owner-avatar" aria-hidden="true">
            {owner?.avatarUrl && <img src={owner.avatarUrl} alt="" />}
          </span>
          <div><h2>{data.owner.name}</h2>
            {owner?.memberSinceYear && <p className="item-owner-since">На платформе с {owner.memberSinceYear} года</p>}
            {data.owner.rating != null && <p className="item-rating">
              <span className="visually-hidden">Рейтинг владельца:</span>
              {data.owner.rating.toLocaleString("ru-RU", { minimumFractionDigits: 1, maximumFractionDigits: 1 })}
              <span aria-hidden="true">☆</span>
            </p>}
          </div>
        </div>
        <Link className="button secondary" href={profile}><img src="/design/user.svg" alt="" />Перейти в профиль</Link>
        <ItemContact item={data} action="message" />
        {reportAction("button secondary item-owner-report")}
      </aside>
    </div>
    <div className="item-right">
      <section className="item-info">
        <div className="item-title">
          <h1>{data.title}</h1>
          {visible && <FavoriteControl id={data.id} type="ITEMS" compact />}
          {reportAction("button secondary item-report")}
        </div>
        {condition && <p className="item-condition">{condition}</p>}
        {(hidden || pending || data.status !== "PUBLISHED") && <span className={!hidden && data.status === "RESERVED" ? "badge reserved" : "badge"}>
          {hidden ? "Закрыто модерацией" : pending ? "На проверке" : itemStatuses[data.status]}
        </span>}
        <div className="item-specs">
          <dl className="item-delivery-method">
            <dt>Способ передачи</dt>
            <dd>{deliveryMethods[data.deliveryMethod ?? "FREE"].label}</dd>
          </dl>
          {(data.size || data.district || published) && <div className="item-facts">
            {data.size && <p><img src="/design/size.svg" alt="" /><span>Размер: <b>{data.size}</b></span></p>}
            {data.district && <p><img src="/design/map.svg" alt="" /><span>Район: <b>{data.district.name}</b></span></p>}
            {published && <p><img src="/design/clock.svg" alt="" /><span>{pending ? "Отправлено на проверку" : "Опубликовано"}: <time dateTime={data.publishedAt ?? undefined}
              title={published.toLocaleDateString("ru-RU")}>{daysPublished ? publicationAge.format(daysPublished) : "сегодня"}</time></span></p>}
          </div>}
          {data.description && <p className="description"><strong>Описание:</strong> {data.description}</p>}
          <ItemContact item={data} action="take" />
        </div>
        {canManage && ["DRAFT", "PUBLISHED"].includes(data.status) && <div className="actions">
          <Link className="button secondary" href={path + "/edit"}>Изменить</Link>
          {data.status === "DRAFT" && <button disabled={busy} onClick={() => change("publish")}>Отправить на проверку</button>}
          <button className="secondary" disabled={busy} onClick={() => change("close")}>Закрыть</button>
        </div>}
        <RequestStatus error={error} />
        {(notice || reported) && <p role="status">{reported ? "Жалоба отправлена." : notice}</p>}
      </section>
      {similar.length > 0 && <section className="item-related">
        <div className="section-head"><h2>Похожие вещи</h2><Link href={categoryPath}>Смотреть все</Link></div>
        <div className="cards">
          {similar.map((entry) => <article className="card" key={entry.id}>
            <Link className="card-image" href={"/items/" + entry.id} aria-label={entry.title}>{entry.photos[0] && <img src={entry.photos[0].url} alt="" />}</Link>
            <div className="card-body">
              <h2><Link href={"/items/" + entry.id}>{entry.title}</Link></h2>
              {entry.district && <p><img src="/design/pin.svg" alt="" />{entry.district.name}</p>}
              {entry.size && <p>Размер: {entry.size}</p>}
              <FavoriteControl id={entry.id} type="ITEMS" compact />
            </div>
          </article>)}
        </div>
      </section>}
    </div>
    <dialog ref={complaint} className="panel item-complaint">
      <form onSubmit={report}>
        <h2>Пожаловаться</h2>
        <label>Причина жалобы<textarea name="reason" rows={4} maxLength={1000} required /></label>
        <RequestStatus error={reportError} />
        <div className="actions">
          <button type="button" className="secondary" disabled={reportBusy} onClick={() => complaint.current?.close()}>Отмена</button>
          <button disabled={reportBusy}>Отправить жалобу</button>
        </div>
      </form>
    </dialog>
  </article>;
}
