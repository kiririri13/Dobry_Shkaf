"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { query } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Category, Condition, District, ItemPage } from "@/lib/types";
import { Categories, Conditions, Districts } from "./Fields";
import RequestStatus from "./RequestStatus";

export const itemStatuses = {
  DRAFT: "Черновик", PUBLISHED: "Опубликовано", RESERVED: "Зарезервировано",
  TRANSFERRED: "Передано", CLOSED: "Закрыто",
};

export default function ItemList({ mine = false, search = "" }: { mine?: boolean; search?: string }) {
  const [filters, setFilters] = useState<Record<string, string>>(search ? { q: search } : {});
  const [term, setTerm] = useState(search);
  const [page, setPage] = useState(0);
  const hasFilters = !mine;
  const categories = useData<Category[]>(hasFilters ? "/categories" : "");
  const districts = useData<District[]>(hasFilters ? "/districts" : "");
  const conditions = useData<Condition[]>("/item-conditions");
  const items = useData<ItemPage>("/items" + query({ ...filters, page, mine: mine ? true : "" }));

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setFilters(Object.fromEntries(Array.from(data, ([key, value]) => [key, String(value).trim()])));
    setPage(0);
  }

  return <>
    <div className="section-head">
      {mine ? <h2>Мои объявления</h2> : <h1>Каталог вещей</h1>}
    </div>
    {hasFilters && <form className="panel filters" onSubmit={applyFilters} onReset={() => {
      setTerm("");
      setFilters({});
      setPage(0);
    }}>
      <label>Поиск<input name="q" type="search" placeholder="Название вещи" value={term} onChange={(event) => setTerm(event.target.value)} /></label>
      <Categories name="categoryId" label="Категория" options={categories.data} />
      <Districts name="districtId" label="Район" options={districts.data} />
      <Conditions name="condition" label="Состояние" options={conditions.data} />
      <label>Размер<input name="size" /></label>
      <label>Доступность<select name="available">
        <option value="">Все вещи</option><option value="true">Доступные</option><option value="false">Зарезервированные</option>
      </select></label>
      <div className="actions">
        <button>Найти</button><button type="reset" className="secondary">Сбросить</button>
      </div>
    </form>}
    <RequestStatus error={categories.error || districts.error || conditions.error} retry={() => {
      categories.reload(); districts.reload(); conditions.reload();
    }} />
    <RequestStatus loading={items.loading} error={items.error} retry={items.reload} />
    {items.data && (items.data.items.length ? <>
      <div className="cards">
        {items.data.items.map((item) => <article className="card" key={item.id}>
          <Link className="card-image" href={"/items/" + item.id}>
            {item.photos[0] ? <img src={item.photos[0].url} alt={item.title} /> : <span>Без фото</span>}
            <span className={item.status === "RESERVED" ? "badge reserved" : "badge"}>
              {item.status === "PUBLISHED" && !mine ? "Можно забрать" : itemStatuses[item.status]}
            </span>
          </Link>
          <div className="card-body">
            <h2><Link href={"/items/" + item.id}>{item.title}</Link></h2>
            <p>{[item.district?.name, item.size, conditions.data?.find((entry) => entry.code === item.condition)?.label].filter(Boolean).join(" · ")}</p>
          </div>
        </article>)}
      </div>
      {items.data.totalPages > 1 && <nav className="actions pagination" aria-label="Страницы">
        <button className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Назад</button>
        <span>{page + 1} / {items.data.totalPages}</span>
        <button className="secondary" disabled={page + 1 >= items.data.totalPages} onClick={() => setPage(page + 1)}>Далее</button>
      </nav>}
    </> : <p>{mine ? "У вас пока нет объявлений." : "Ничего не найдено. Попробуйте изменить фильтры."}</p>)}
  </>;
}
