"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { query } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Category, Condition, District, ItemPage } from "@/lib/types";
import { Categories, Conditions, Districts } from "./Fields";
import RequestStatus from "./RequestStatus";
import { FavoriteControl } from "./FavoriteButton";

export const itemStatuses = {
  DRAFT: "Черновик", PUBLISHED: "Опубликовано", RESERVED: "Зарезервировано",
  TRANSFERRED: "Передано", CLOSED: "Закрыто",
};

export default function ItemList({ mine = false, preview = false, search = "", category = "" }: { mine?: boolean; preview?: boolean; search?: string; category?: string }) {
  const [filters, setFilters] = useState<Record<string, string>>({ q: search, categoryId: category });
  const [term, setTerm] = useState(search);
  const [selectedCategory, setSelectedCategory] = useState(category);
  const [page, setPage] = useState(0);
  const [previewStart, setPreviewStart] = useState(0);
  const hasFilters = !mine && !preview;
  const categories = useData<Category[]>(hasFilters ? "/categories" : "");
  const districts = useData<District[]>(hasFilters ? "/districts" : "");
  const conditions = useData<Condition[]>(preview ? "" : "/item-conditions");
  const items = useData<ItemPage>("/items" + query({ ...filters, page, mine: mine ? true : "" }));
  const previewEnd = Math.max(0, (items.data?.items.length ?? 0) - 4);
  const previewIndex = Math.min(previewStart, previewEnd);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setFilters(Object.fromEntries(Array.from(data, ([key, value]) => [key, String(value).trim()])));
    setPage(0);
  }

  return <>
    {!mine && <div className="section-head">
      {preview ? <><h2><img className="home-icon" src="/design/home-nearby-pin.svg" alt="" />Вещи рядом</h2>
        <Link className="home-link" href="/items">Смотреть все<img className="home-icon" src="/design/home-chevron.svg" alt="" /></Link></> : <h1>Каталог вещей</h1>}
    </div>}
    {hasFilters && <form className="panel filters" onSubmit={applyFilters} onReset={() => {
      setTerm("");
      setSelectedCategory("");
      setFilters({});
      setPage(0);
    }}>
      <label>Поиск<input name="q" type="search" placeholder="Название вещи" value={term} onChange={(event) => setTerm(event.target.value)} /></label>
      <Categories name="categoryId" label="Категория" value={selectedCategory} options={categories.data} onChange={setSelectedCategory} />
      <Districts name="districtId" label="Район" options={districts.data} />
      <Conditions name="condition" label="Состояние" options={conditions.data} />
      <label>Размер<input name="size" /></label>
      <div className="actions">
        <button>Найти</button><button type="reset" className="secondary">Сбросить</button>
      </div>
    </form>}
    <RequestStatus error={categories.error || districts.error || conditions.error} retry={() => {
      categories.reload(); districts.reload(); conditions.reload();
    }} />
    <RequestStatus loading={items.loading} error={items.error} retry={items.reload} />
    {items.data && (items.data.items.length ? <>
      <div className={mine ? "cards profile-cards" : preview ? "cards preview-cards" : "cards"}>
        {(preview ? items.data.items.slice(previewIndex, previewIndex + 4) : items.data.items).map((item) => <article className="card" key={item.id}>
          <Link className="card-image" href={"/items/" + item.id} aria-label={item.title}>
            {item.photos[0] ? <img src={item.photos[0].url} alt={item.title} /> : !preview && <span>Без фото</span>}
            {!preview && <span className={item.status === "RESERVED" ? "badge reserved" : "badge"}>
              {item.moderationStatus === "HIDDEN" ? "Закрыто модерацией" : item.status === "PUBLISHED" && !mine ? "Можно забрать" : itemStatuses[item.status]}
            </span>}
          </Link>
          <div className="card-body">
            <h2><Link href={"/items/" + item.id}>{item.title}</Link></h2>
            {preview ? <>
              {item.district && <p><img src="/design/pin.svg" alt="" />{item.district.name}</p>}
              {item.size && <p>Размер: {item.size}</p>}
              <FavoriteControl id={item.id} type="ITEMS" compact />
            </> : <p>{[item.district?.name, item.size, conditions.data?.find((entry) => entry.code === item.condition)?.label].filter(Boolean).join(" · ")}</p>}
          </div>
        </article>)}
      </div>
      {preview && previewEnd > 0 && <nav className="preview-list-controls" aria-label="Вещи рядом">
        {previewIndex > 0 && <button type="button" className="preview-back" aria-label="Предыдущие вещи" onClick={() => setPreviewStart(Math.max(0, previewIndex - 4))} />}
        <button type="button" className="preview-next" aria-label="Следующие вещи" disabled={previewIndex >= previewEnd}
          onClick={() => setPreviewStart(Math.min(previewEnd, previewIndex + 4))} />
      </nav>}
      {!preview && items.data.totalPages > 1 && <nav className="actions pagination" aria-label="Страницы">
        <button className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Назад</button>
        <span>{page + 1} / {items.data.totalPages}</span>
        <button className="secondary" disabled={page + 1 >= items.data.totalPages} onClick={() => setPage(page + 1)}>Далее</button>
      </nav>}
    </> : <p>{mine ? "У вас пока нет объявлений." : "Ничего не найдено. Попробуйте изменить фильтры."}</p>)}
  </>;
}
