"use client";

import Link from "next/link";
import ItemList from "@/components/ItemList";
import PointMap from "@/components/PointMap";
import { useData } from "@/lib/useData";
import { query } from "@/lib/api";
import type { Category, Point } from "@/lib/types";

const popular = [
  ["Верхняя одежда", "outerwear"],
  ["Обувь", "shoes"],
  ["Аксессуары", "accessories"],
  ["Джинсы", "jeans"],
];

function HomeIcon({ name }: { name: string }) {
  return <img className="home-icon" src={`/design/home-${name}.svg`} alt="" />;
}

function PointPhoto({ point }: { point: Point }) {
  return <span className="point-list-image" aria-hidden="true">
    <img className={point.photoUrl ? "point-photo" : undefined} src={point.photoUrl || "/design/map.svg"} alt="" />
  </span>;
}

function PointSummary({ point }: { point: Point }) {
  return <div>
    <h3><Link href={"/points/" + point.id}>{point.name}</Link></h3>
    <p>{point.address}</p>
    <span className="badge">{point.temporarilyClosed ? "Временно закрыт" : point.hours}</span>
  </div>;
}

export default function Home() {
  const categories = useData<Category[]>("/categories");
  const points = useData<Point[]>("/points");
  const stats = useData<{ completedTransfers: number; closedListings: number; contribution: { weightKg: number | null } }>("/stats");
  const weightKg = stats.data?.contribution.weightKg;
  const [firstPoint, ...otherPoints] = points.data ?? [];

  return <>
    <div className="home-layout">
      <div>
        <section className="hero">
          <h1>Дай вещам<br />новую жизнь</h1>
          <p>Найди то, что нужно, или отдай<br /> то, чем больше не пользуешься</p>
        </section>
        <div className="home-actions">
          <Link href="/items" className="home-find"><span className="home-action-icon"><HomeIcon name="search" /></span><span><strong>Найти вещь</strong>Открой для себя уникальные вещи рядом с тобой</span><HomeIcon name="action-arrow" /></Link>
          <Link href="/items/new" className="home-give"><span className="home-action-icon"><HomeIcon name="plus" /></span><span><strong>Отдать вещь</strong>Поделись вещами, которыми больше не пользуешься</span><HomeIcon name="action-arrow" /></Link>
        </div>
        <section className="home-items"><ItemList preview /></section>
      </div>
      <aside className="home-side">
        <section className="home-stats">
          <div className="home-stats-heading">
            <span><HomeIcon name="heart" /></span>
            <div><h2>Ваш вклад</h2><p>Вместе мы делаем<br /> город чище и добрее</p></div>
          </div>
          <dl>
            <div><dt>Передано<br /> вещей</dt><dd>{stats.data?.completedTransfers.toLocaleString("ru-RU") ?? "—"}</dd><HomeIcon name="package" /></div>
            <div><dt>Сокращено<br /> отходов</dt><dd>{weightKg == null ? "—" : weightKg.toLocaleString("ru-RU") + " кг"}</dd><HomeIcon name="truck" /></div>
            <div className="home-stats-closed"><dt>Закрыто объявлений</dt><dd>{stats.data?.closedListings?.toLocaleString("ru-RU") ?? "—"}</dd><img src="/design/archive.svg" alt="" /></div>
          </dl>
          <p className="home-stats-note"><HomeIcon name="trend" />Даже маленькие действия меняют мир</p>
          <Link className="home-link" href="/profile">Подробнее<HomeIcon name="link-arrow" /></Link>
        </section>
        <section className="home-categories">
        <h2>Популярные категории</h2>
        <ul className="activity-list">
          {popular.map(([name, image]) => {
            const category = categories.data?.find((entry) => entry.name.toLowerCase() === name.toLowerCase());
            return <li key={image}>
              <Link href={"/items" + query(category ? { categoryId: category.id } : { q: name })}>
                <span>{name}</span><img src={`/design/category-${image}.webp`} alt="" />
              </Link>
            </li>;
          })}
        </ul>
        <Link className="home-link" href="/items">Все категории<HomeIcon name="link-arrow" /></Link>
        </section>
      </aside>
    </div>
    <section className="home-points">
      <div className="home-points-overview">
        <header>
          <HomeIcon name="map-pin" />
          <div><h2>Рядом с вами</h2><p>Пункты приема и где можно сдать вещи</p></div>
          <Link className="button" href="/points">Открыть карту</Link>
        </header>
        {points.data && points.data.length > 0 && <PointMap points={points.data} compact />}
      </div>
      {firstPoint && <div className="home-points-near">
        <h2>Пункты на карте</h2><p>Удобно когда рядом</p>
        <div className="home-points-layout">
          <article className="panel">
            <div className="point-list-details">
              <PointPhoto point={firstPoint} />
              <PointSummary point={firstPoint} />
            </div>
            <Link className="home-link" href="/points">Открыть на карте<HomeIcon name="link-arrow" /></Link>
          </article>
          <ul className="activity-list">
            {otherPoints.slice(0, 3).map((point) => <li key={point.id}>
              <div className="point-list-details"><PointPhoto point={point} /><PointSummary point={point} /></div>
            </li>)}
          </ul>
        </div>
      </div>}
      {otherPoints.length > 3 && <div className="home-points-other">
        <h2>Еще также</h2><p>Пункты, которым доверяют</p>
        <ul className="activity-list">{otherPoints.slice(3, 6).map((point) => <li key={point.id}>
          <Link href={"/points/" + point.id}><HomeIcon name="point-pin" /><span>{point.name}</span><HomeIcon name="double-chevron" /></Link>
        </li>)}</ul>
        <Link className="home-link" href="/points">Посмотреть еще<HomeIcon name="link-arrow" /></Link>
      </div>}
    </section>
  </>;
}
