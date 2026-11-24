"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import RequestStatus from "./RequestStatus";
import { useData } from "@/lib/useData";
import type { ItemPage, Profile, Review } from "@/lib/types";
import ReviewSummary from "./ReviewSummary";
import ProfileTabs from "./ProfileTabs";
import ProfileEco from "./ProfileEco";
import PublicProfileHistory from "./PublicProfileHistory";
import { useSession } from "./Session";
import { api, errorMessage } from "@/lib/api";

export default function PublicProfile({ id, initialSection }: { id: string; initialSection: number }) {
  const { user } = useSession();
  const [section, setSection] = useState(initialSection);
  const [itemsPage, setItemsPage] = useState(0);
  const [reporting, setReporting] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const path = "/profiles/" + encodeURIComponent(id);
  const profile = useData<Profile>(path);
  const reviews = useData<Review[]>(path + "/reviews");
  const items = useData<ItemPage>(path + "/items?page=" + itemsPage);
  if (!profile.data) return <RequestStatus loading={profile.loading} error={profile.error} retry={profile.reload} />;
  const { name, district, rating, completedTransfers, avatarUrl, memberSinceYear } = profile.data;

  async function report(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const reason = String(new FormData(event.currentTarget).get("reason") ?? "").trim();
    if (busy || !reason) return;
    setBusy(true);
    setError("");
    try {
      await api("/complaints", { method: "POST", json: { targetType: "PROFILE", targetId: id, reason } });
      setNotice("Жалоба отправлена.");
      setReporting(false);
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <section className="review-page">
    <p className="breadcrumbs"><Link href="/">Главная</Link> / Профиль</p>
    <div className="profile">
      <div className="profile-cover" />
      <div className="profile-info">
        <div className="profile-avatar" aria-hidden="true">{avatarUrl && <img src={avatarUrl} alt="" />}</div>
        <div className="profile-name"><h1>{name}</h1>
          {district && <p className="profile-district"><img src="/design/pin.svg" alt="" />{district.name}, {district.city}</p>}
          {memberSinceYear && <p className="public-profile-since">На платформе с {memberSinceYear} года</p>}
          {profile.data.bio && <p className="profile-bio">{profile.data.bio}</p>}
        </div>
        {user === null && <Link className="button secondary profile-report" href={"/login?next=" + encodeURIComponent("/profiles/" + id)}>Пожаловаться</Link>}
        {user && user.id !== id && <button type="button" className="secondary profile-report" disabled={Boolean(notice)} onClick={() => setReporting(!reporting)}>Пожаловаться</button>}
      </div>
      {reporting && <form className="panel profile-edit-form" onSubmit={report}>
        <label>Причина жалобы<textarea name="reason" maxLength={1000} rows={3} required /></label>
        <button disabled={busy}>Отправить жалобу</button>
        <RequestStatus error={error} />
      </form>}
      {notice && <p role="status">{notice}</p>}
      <dl className="profile-stats">
        <div><img src="/design/profile-listings.svg" alt="" /><dt>Выложено вещей</dt><dd>{items.data?.totalElements ?? "—"}</dd></div>
        <div><img src="/design/profile-given.svg" alt="" /><dt>Передачи</dt><dd>{completedTransfers}</dd></div>
        <div><img src="/design/profile-rating.svg" alt="" /><dt>Рейтинг</dt><dd>{rating?.toFixed(1) ?? "—"}</dd></div>
        <div><img src="/design/profile-reviews.svg" alt="" /><dt>Отзывов</dt><dd>{reviews.data?.length === 20 ? "20+" : reviews.data?.length ?? "—"}</dd></div>
      </dl>
    </div>
    <ProfileTabs active={section} onChange={setSection} />
    <div className="profile-tab-content">
      {section === 0 && <section aria-label="Объявления пользователя">
        <RequestStatus loading={items.loading} error={items.error} retry={items.reload} />
        {items.data?.items.length === 0 && <p>Объявлений пока нет.</p>}
        <div className="cards profile-cards">
          {items.data?.items.map((item) => <article className="card" key={item.id}>
            <Link className="card-image" href={"/items/" + encodeURIComponent(item.id)} aria-label={item.title}>
              {item.photos[0] && <img src={item.photos[0].url} alt="" />}
            </Link>
            <div className="card-body">
              <h2><Link href={"/items/" + encodeURIComponent(item.id)}>{item.title}</Link></h2>
              {item.district && <p>{item.district.name}</p>}
              {item.size && <p>Размер: {item.size}</p>}
            </div>
          </article>)}
        </div>
        {items.data && items.data.totalPages > 1 && <nav className="actions pagination" aria-label="Страницы объявлений">
          <button type="button" className="secondary" disabled={itemsPage === 0} onClick={() => setItemsPage(itemsPage - 1)}>Назад</button>
          <span>{itemsPage + 1} / {items.data.totalPages}</span>
          <button type="button" className="secondary" disabled={itemsPage + 1 >= items.data.totalPages} onClick={() => setItemsPage(itemsPage + 1)}>Далее</button>
        </nav>}
      </section>}
      {section === 1 && <PublicProfileHistory key={id} path={path + "/history"} />}
      {section === 3 && <ProfileEco completedTransfers={completedTransfers} />}
      {section === 2 && <>
        <RequestStatus loading={reviews.loading} error={reviews.error} retry={reviews.reload} />
        {reviews.data && <ReviewSummary rating={rating} reviews={reviews.data} />}
      </>}
    </div>
  </section>;
}
