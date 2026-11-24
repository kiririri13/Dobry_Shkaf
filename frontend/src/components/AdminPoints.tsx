"use client";

import { useState, type FormEvent } from "react";
import { api, ApiError, errorMessage, query } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Category, District, Point } from "@/lib/types";
import { Districts } from "./Fields";
import { pointStatuses } from "./Points";
import RequestStatus from "./RequestStatus";
import PointMap, { type Coordinates } from "./PointMap";

function contactFields(value: string) {
  const lines = value.split(/\r?\n/);
  const first = lines[0].trim();
  if (first.startsWith("Телефон: ")) {
    return { phone: first.slice("Телефон: ".length), website: lines.slice(1).join(" ").trim() };
  }
  if (/^\+?[\d ()-]{6,}$/.test(first)) return { phone: first, website: lines.slice(1).join(" ").trim() };
  return { phone: "", website: lines.join(" ").trim() };
}

function PointLabel({ children }: { children: string }) {
  return <span>{children}<span className="point-required" aria-hidden="true">*</span></span>;
}

function PointForm({ point, close, saved }: { point?: Point; close: () => void; saved: () => void }) {
  const categories = useData<Category[]>("/categories");
  const districts = useData<District[]>("/districts");
  const type = point?.type ?? "EXCHANGE";
  const initialContacts = contactFields(point?.contacts ?? "");
  const [selected, setSelected] = useState<Coordinates>();
  const [mapPoints] = useState<Point[]>(() => point ? [point] : []);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const data = new FormData(event.currentTarget);
    const value = (name: string) => String(data.get(name) || "").trim();
    const categoryIds = data.getAll("categoryIds").map(String);
    const phone = value("contactPhone");
    const website = value("contactWebsite");
    const contacts = point && phone === initialContacts.phone && website === initialContacts.website
      ? point.contacts : [phone && "Телефон: " + phone, website].filter(Boolean).join("\n");
    if (!categoryIds.length) return setError("Выберите хотя бы одну категорию.");
    if (!contacts.trim() || contacts.length > 500) return setError("Укажите телефон или сайт. Общая длина контактов должна быть не больше 500 символов.");
    setBusy(true);
    setError("");
    try {
      await api(point ? "/admin/points/" + point.id : "/admin/points", {
        method: point ? "PUT" : "POST",
        json: {
          name: value("name"), organization: value("organization"), address: value("address"),
          hours: value("hours"), contacts, source: value("source"),
          type, categoryIds, districtId: value("districtId"), latitude: Number(value("latitude")),
          longitude: Number(value("longitude")), verifiedAt: value("verifiedAt"), conditions: value("conditions"),
          receivingConditions: type === "EXCHANGE" ? value("receivingConditions") : null, status: value("status"),
        },
      });
      saved();
    } catch (failure) {
      const details = failure instanceof ApiError ? Object.values(failure.fields) : [];
      setError([errorMessage(failure), ...details].join(" "));
    } finally {
      setBusy(false);
    }
  }

  const loading = categories.loading || districts.loading;
  const referenceError = categories.error || districts.error;
  return <section className="moderation-point-form">
    <form onSubmit={submit} onInvalidCapture={(event) => {
      if (event.target instanceof HTMLElement) {
        const details = event.target.closest("details");
        if (details) details.open = true;
      }
    }}>
      <div className="panel moderation-point-fields">
        <h2>Основная информация</h2>
        <RequestStatus loading={loading} error={referenceError} retry={() => { categories.reload(); districts.reload(); }} />
        {!loading && !referenceError && <fieldset disabled={busy}>
          <div className="moderation-point-primary">
            <label><PointLabel>Название точки</PointLabel><input name="name" defaultValue={point?.name} maxLength={200} required placeholder="Введите название организации" /></label>
            <div className="moderation-point-categories">
              <span id="point-category-label"><PointLabel>Категория обмена</PointLabel></span>
              <details>
                <summary><img src="/design/point-form-category.svg" alt="" />Выберите категорию</summary>
                <div className="point-category-options" role="group" aria-labelledby="point-category-label">
                  {categories.data?.map((category) => <label key={category.id}>
                    {category.name}
                    <input className="visually-hidden" name="categoryIds" type="checkbox" value={category.id} defaultChecked={point?.categories.some((entry) => entry.id === category.id)} />
                    <span className="option-check" aria-hidden="true" />
                  </label>)}
                </div>
              </details>
            </div>
            <label className="moderation-point-address"><PointLabel>Адрес</PointLabel>
              <span className="moderation-point-input"><img src="/design/pin.svg" alt="" />
                <input name="address" defaultValue={point?.address} maxLength={300} required placeholder="Укажите адрес" />
              </span>
            </label>
          </div>
          <PointMap points={mapPoints} onPick={setSelected} selection={selected} />
          <label className="moderation-point-schedule"><PointLabel>Часы работы</PointLabel>
            <span className="moderation-point-input"><img src="/design/clock.svg" alt="" />
              <input name="hours" defaultValue={point?.hours} maxLength={500} required placeholder="Например: 09:00 - 21:00" />
            </span>
          </label>
          <label><PointLabel>Описание</PointLabel><textarea name="conditions" defaultValue={point?.conditions} maxLength={2000} required placeholder="Расскажите подробнее о точке обмена..." /></label>
          <fieldset className="moderation-point-contacts">
            <legend>Контактная информация</legend>
            <label className="moderation-point-input"><span className="visually-hidden">Номер телефона</span><img src="/design/point-form-phone.svg" alt="" />
              <input type="tel" name="contactPhone" defaultValue={initialContacts.phone} maxLength={500} placeholder="Номер телефона" />
            </label>
            <label className="moderation-point-input"><span className="visually-hidden">Сайт или соцсети</span><img src="/design/point-form-link.svg" alt="" />
              <input name="contactWebsite" defaultValue={initialContacts.website} maxLength={500} placeholder="Сайт или соцсети" />
            </label>
          </fieldset>
          <details className="moderation-point-extra">
            <summary>Сведения о пункте и координаты</summary>
            <div className="filters">
              <label>Статус<select name="status" defaultValue={point?.status || "ACTIVE"}>
                <option value="ACTIVE">Работает</option><option value="TEMPORARILY_CLOSED">Временно закрыт</option>
              </select></label>
              <label>Организация<input name="organization" defaultValue={point?.organization.name} maxLength={200} required /></label>
              <label>Источник<input name="source" defaultValue={point?.source} maxLength={300} required /></label>
              <Districts name="districtId" label="Район" options={districts.data} value={point?.district.id} required />
              <label>Широта<input key={selected?.lat} name="latitude" type="number" step="any" min={-90} max={90} defaultValue={selected?.lat ?? point?.latitude} required /></label>
              <label>Долгота<input key={selected?.lng} name="longitude" type="number" step="any" min={-180} max={180} defaultValue={selected?.lng ?? point?.longitude} required /></label>
              <label>Дата проверки<input name="verifiedAt" type="date" defaultValue={point?.verifiedAt} required /></label>
            </div>
            {type === "EXCHANGE" && <label>Условия получения<textarea name="receivingConditions" defaultValue={point?.receivingConditions || ""} maxLength={2000} required /></label>}
          </details>
        </fieldset>}
      </div>
      <div className="actions moderation-point-actions">
        <button type="button" className="secondary" disabled={busy} onClick={close}>Отмена</button>
        <button type="submit" disabled={busy || loading || Boolean(referenceError)}>{busy ? "Сохраняем…" : point ? "Сохранить изменения" : "Сохранить пункт"}</button>
      </div>
    </form>
    <RequestStatus error={error} />
  </section>;
}

export default function AdminPoints() {
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState<Point | "new" | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const points = useData<Point[]>("/admin/points" + query({ status }));

  async function archive(point: Point) {
    if (busy || !window.confirm("Архивировать пункт «" + point.name + "»?")) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api("/admin/points/" + point.id + "/archive", { method: "POST" });
      setNotice("Пункт перенесен в архив.");
      points.reload();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  if (editing) return <PointForm key={editing === "new" ? editing : editing.id}
    point={editing === "new" ? undefined : editing} close={() => setEditing(null)} saved={() => {
      setEditing(null);
      setNotice("Пункт сохранен.");
      points.reload();
    }} />;

  return <>
    <div className="section-head"><h1>Управление пунктами</h1>
      <button disabled={busy} onClick={() => { setNotice(""); setEditing("new"); }}>Добавить пункт</button>
    </div>
    <label className="status-filter">Статус<select value={status} onChange={(event) => setStatus(event.target.value)}>
      <option value="">Все статусы</option>
      {Object.entries(pointStatuses).map(([key, value]) => <option key={key} value={key}>{value}</option>)}
    </select></label>
    <RequestStatus error={error} />
    <RequestStatus loading={points.loading} error={points.error} retry={points.reload} />
    {notice && <p role="status">{notice}</p>}
    {points.data?.length === 0 && <p>Пунктов с таким статусом нет.</p>}
    <ul className="point-list admin-points">
      {points.data?.map((point) => <li className="panel" key={point.id}>
        <h2>{point.name}</h2>
        <p>{point.address}</p>
        <p><span className="badge">{pointStatuses[point.status]}</span></p>
        {point.status !== "ARCHIVED" && <div className="actions">
          <button className="secondary" disabled={busy} onClick={() => { setNotice(""); setEditing(point); }}>Изменить</button>
          <button className="secondary" disabled={busy || Boolean(editing)} onClick={() => archive(point)}>В архив</button>
        </div>}
      </li>)}
    </ul>
  </>;
}
