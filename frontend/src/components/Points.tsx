"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { query } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Category, District, Point } from "@/lib/types";
import { Categories, Districts } from "./Fields";
import PointMap from "./PointMap";
import RequestStatus from "./RequestStatus";
import { FavoriteControl } from "./FavoriteButton";

const pointTypes = { COLLECTION: "Пункт приема", EXCHANGE: "Пункт обмена" };
export const pointStatuses = { ACTIVE: "Работает", TEMPORARILY_CLOSED: "Временно закрыт", ARCHIVED: "В архиве" };

export default function Points() {
  const [filters, setFilters] = useState<Record<string, string | boolean>>({});
  const [focus, setFocus] = useState<{ id: string }>();
  const categories = useData<Category[]>("/categories");
  const districts = useData<District[]>("/districts");
  const points = useData<Point[]>("/points" + query(filters));
  const [lastPoints, setLastPoints] = useState<Point[]>();
  const mapPoints = points.data ?? lastPoints;

  useEffect(() => {
    if (points.data) setLastPoints(points.data);
  }, [points.data]);

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = Object.fromEntries(Array.from(data, ([key, value]) => [key, String(value).trim()]));
    setFilters(next);
  }

  return <section className="points-page">
    <h1 className="visually-hidden">Пункты приема и обмена</h1>
    <form className="panel filters points-filters" onSubmit={apply} onReset={() => setFilters({})}>
      <label>Название<input name="q" type="search" placeholder="Поиск по названию" /></label>
      <label>Тип<select name="type">
        <option value="">Все типы</option>
        {Object.entries(pointTypes).map(([type, label]) => <option key={type} value={type}>{label}</option>)}
      </select></label>
      <Categories name="categoryId" label="Категория обмена" options={categories.data} />
      <Districts name="districtId" label="Районы" options={districts.data} />
      <div className="actions"><button type="reset" className="secondary">Сбросить</button><button>Найти</button></div>
    </form>
    <RequestStatus error={categories.error || districts.error} retry={() => { categories.reload(); districts.reload(); }} />
    <RequestStatus loading={points.loading} error={points.error} retry={points.reload} />
    {mapPoints && <div className="points-layout">
      <PointMap points={mapPoints} focus={focus} showFirst />
      {points.data && (points.data.length ? <ul className="point-list" aria-label="Список пунктов">
        {points.data.map((point) => <li className="panel" key={point.id}>
          <div className="point-list-details">
            <span className="point-list-image" aria-hidden="true"><img className={point.photoUrl ? "point-photo" : undefined} src={point.photoUrl || "/design/map.svg"} alt="" /></span>
            <div>
              <h2><Link href={"/points/" + point.id}>{point.name}</Link></h2>
              <p>{point.address}</p>
              <p>{pointTypes[point.type]} · {point.district.name}</p>
              <p><span className="badge">{point.temporarilyClosed ? pointStatuses[point.status] : point.hours}</span></p>
            </div>
          </div>
          <button type="button" className="secondary point-show-map" onClick={() => setFocus({ id: point.id })}>Открыть на карте <span aria-hidden="true">→</span></button>
        </li>)}
      </ul> : <p>Ничего не найдено. Попробуйте выбрать другой район или категорию.</p>)}
    </div>}
  </section>;
}

export function PointDetails({ id }: { id: string }) {
  const point = useData<Point>("/points/" + encodeURIComponent(id));
  if (!point.data) return <RequestStatus loading={point.loading} error={point.error} retry={point.reload} />;
  const data = point.data;
  return <article className="panel detail">
    <Link href="/points">К списку пунктов</Link>
    <h1>{data.name}</h1>
    <FavoriteControl key={data.id} id={data.id} type="POINTS" />
    <p>{pointTypes[data.type]} · <span className="badge">{pointStatuses[data.status]}</span></p>
    <dl>
      <dt>Организация</dt><dd>{data.organization.name}</dd>
      <dt>Адрес</dt><dd>{data.address} ({data.district.name})</dd>
      <dt>Режим работы</dt><dd>{data.hours}</dd>
      <dt>Контакты</dt><dd>{data.contacts}</dd>
      <dt>Что принимают</dt><dd>{data.categories.map((category) => category.name).join(", ")}</dd>
      <dt>Условия приема</dt><dd>{data.conditions}</dd>
      {data.receivingConditions && <><dt>Как получить вещи</dt><dd>{data.receivingConditions}</dd></>}
      <dt>Источник</dt><dd>{data.source}</dd>
      <dt>Дата проверки</dt><dd>{data.verifiedAt}</dd>
    </dl>
  </article>;
}
