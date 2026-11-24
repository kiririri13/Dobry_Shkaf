"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useRef, useState, type FormEvent } from "react";
import { PrivatePage, useSession } from "@/components/Session";
import RequestStatus from "@/components/RequestStatus";
import ItemList from "@/components/ItemList";
import { Districts } from "@/components/Fields";
import ReviewSummary from "@/components/ReviewSummary";
import ProfileTabs from "@/components/ProfileTabs";
import ProfileEco from "@/components/ProfileEco";
import { api, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { District, EcoStats, ItemPage, Profile as ProfileData, Review, Transfer } from "@/lib/types";
import { transferStatuses } from "@/components/Transfers";

export default function ProfilePage() {
  return <PrivatePage><Suspense fallback={<RequestStatus loading />}><ProfileFromLink /></Suspense></PrivatePage>;
}

function ProfileFromLink() {
  const initialSection = useSearchParams().get("section") === "reviews" ? 2 : 0;
  return <Profile key={initialSection} initialSection={initialSection} />;
}

function Profile({ initialSection }: { initialSection: number }) {
  const { user, setUser } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [section, setSection] = useState(initialSection);
  const [editing, setEditing] = useState(false);
  const [avatarBusy, setAvatarBusy] = useState(false);
  const [avatarVersion, setAvatarVersion] = useState(0);
  const avatarInput = useRef<HTMLInputElement>(null);
  const [historyOrder, setHistoryOrder] = useState("recent");
  const self = useData<{ profile: ProfileData }>("/profile/me");
  const districts = useData<District[]>(editing ? "/districts" : "");
  const reviews = useData<Review[]>(user ? "/profiles/" + encodeURIComponent(user.id) + "/reviews" : "");
  const eco = useData<EcoStats>(section === 3 ? "/profile/me/stats" : "");
  const profile = self.data?.profile;
  const listings = useData<ItemPage>("/items?mine=true");
  const transfers = useData<Transfer[]>("/transfers");
  const history = historyOrder === "oldest" ? [...(transfers.data ?? [])].reverse() : transfers.data ?? [];
  const given = transfers.data?.filter((transfer) => transfer.myRole === "GIVER" && transfer.status === "COMPLETED").length;
  const stats = [
    ["profile-listings", "Выложено вещей", listings.data?.totalElements],
    ["profile-given", "Отдано", given],
    ["profile-rating", "Рейтинг", profile?.rating?.toFixed(1)],
    ["profile-reviews", "Отзывов", reviews.data?.length === 20 ? "20+" : reviews.data?.length],
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

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || avatarBusy || !user) return;
    const data = new FormData(event.currentTarget);
    const name = String(data.get("name") ?? "").trim();
    if (!name) return setError("Введите имя.");
    setBusy(true);
    setError("");
    try {
      const saved = await api<{ profile: ProfileData }>("/profile/me", {
        method: "PUT", json: { name, districtId: data.get("districtId") || null, bio: String(data.get("bio") ?? "").trim() },
      });
      setUser({ ...user, name: saved.profile.name });
      setEditing(false);
      self.reload();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  async function changeAvatar(file?: File) {
    if (!profile || busy || avatarBusy) return;
    setError("");
    if (file && (!file.size || file.size > 5 * 1024 * 1024 || !["image/jpeg", "image/png", "image/webp"].includes(file.type))) {
      setError("Выберите непустое фото JPEG, PNG или WebP размером до 5 МБ.");
      return;
    }
    setAvatarBusy(true);
    try {
      if (file) {
        const form = new FormData();
        form.append("file", file);
        await api("/profile/me/avatar", { method: "PUT", form });
      } else {
        await api("/profile/me/avatar", { method: "DELETE" });
      }
      setAvatarVersion(Date.now());
      self.reload();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setAvatarBusy(false);
    }
  }

  return <>
    <p className="breadcrumbs"><Link href="/">Главная</Link> / Профиль</p>
    <section className="profile">
      <div className="profile-cover">
        <button type="button" className="secondary profile-edit" onClick={() => { setError(""); setEditing(!editing); }} aria-expanded={editing}>
          <img src="/design/profile-edit.svg" alt="" />Редактировать профиль
        </button>
      </div>
      <div className="profile-info">
        <div className="profile-avatar">
          {profile?.avatarUrl && <img src={profile.avatarUrl + (avatarVersion ? "?v=" + avatarVersion : "")} alt="" />}
          <input ref={avatarInput} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={(event) => {
            const file = event.currentTarget.files?.[0];
            event.currentTarget.value = "";
            if (file) void changeAvatar(file);
          }} />
          <button type="button" className="profile-avatar-edit" disabled={!profile || busy || avatarBusy}
            aria-label={avatarBusy ? "Обновляем фото профиля" : "Изменить фото профиля"} onClick={() => avatarInput.current?.click()}>
            <img src="/design/profile-avatar-edit.svg" alt="" />
          </button>
        </div>
        <div className="profile-name"><h1>{profile?.name ?? user?.name}</h1>
          {profile?.district && <p className="profile-district"><img src="/design/pin.svg" alt="" />{profile.district.name}, {profile.district.city}</p>}
          {profile?.bio && <p className="profile-bio">{profile.bio}</p>}
        </div>
        <button type="button" className="secondary profile-logout" onClick={logout} disabled={busy || avatarBusy}>{busy ? "Выходим…" : "Выйти"}</button>
      </div>
      {editing && <form className="panel profile-edit-form" onSubmit={save}>
        <RequestStatus loading={self.loading || districts.loading} error={self.error || districts.error}
          retry={() => { self.reload(); districts.reload(); }} />
        {profile && districts.data && <fieldset disabled={busy || avatarBusy}>
          <label>Имя<input name="name" defaultValue={profile.name} maxLength={100} required /></label>
          <Districts name="districtId" label="Район" value={profile.district?.id} options={districts.data} />
          <label>О себе<textarea name="bio" defaultValue={profile.bio ?? ""} rows={3} maxLength={1000} /></label>
          <div className="actions">
            <button>Сохранить</button>
            {profile.avatarUrl && <button className="secondary" type="button" onClick={() => changeAvatar()}>Удалить фото</button>}
            <button className="secondary" type="button" onClick={() => setEditing(false)}>Отмена</button>
          </div>
        </fieldset>}
      </form>}
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
    <ProfileTabs own active={section} onChange={setSection} />
    {section === 0 && <section className="profile-items" aria-label="Мои объявления">
      <div className="profile-sort">Сортировка <img src="/design/profile-sort.svg" alt="" /> Сначала новые</div>
      <ItemList mine />
    </section>}
    {section === 1 && <section aria-label="История">
      <div className="profile-history-link">
        <label className="profile-sort">Сортировка
          <select value={historyOrder} onChange={(event) => setHistoryOrder(event.target.value)}>
            <option value="recent">Сначала новые</option>
            <option value="oldest">Сначала старые</option>
          </select>
        </label>
        <Link href="/profile/transfers">Все заявки и передачи</Link>
      </div>
      <RequestStatus loading={transfers.loading} error={transfers.error} retry={transfers.reload} />
      {transfers.data?.length === 0 && <p>Заявок и передач пока нет.</p>}
      <div className="cards profile-cards profile-history-cards">
        {history.map((transfer) => {
          const href = "/profile/transfers/" + encodeURIComponent(transfer.id);
          const outgoing = transfer.myRole === "GIVER";
          const status = transfer.status === "COMPLETED" ? outgoing ? "Отдали" : "Получили" : transferStatuses[transfer.status];
          const date = transfer.completedAt ?? transfer.updatedAt;
          return <article className="card" key={transfer.id} data-status={transfer.status}>
            <Link className="card-image" href={href} aria-label={transfer.item.title}>
              {transfer.item.photoUrl && <img src={transfer.item.photoUrl} alt="" />}
              <span className="badge">{status}</span>
            </Link>
            <div className="card-body">
              <h2><Link href={href}>{transfer.item.title}</Link></h2>
              {transfer.item.size && <p>Размер: {transfer.item.size}</p>}
              {date && <p><time dateTime={date}>{new Date(date).toLocaleDateString("ru-RU")}</time></p>}
              <p>{outgoing ? "Получатель: " + transfer.receiver.name : "Владелец: " + transfer.giver.name}</p>
            </div>
          </article>;
        })}
      </div>
    </section>}
    {section === 2 && <section className="profile-tab-content" aria-label="Отзывы">
      <RequestStatus loading={reviews.loading} error={reviews.error} retry={reviews.reload} />
      {reviews.data && <ReviewSummary rating={profile?.rating} reviews={reviews.data} />}
    </section>}
    {section === 3 && <section className="profile-tab-content" aria-label="Вклад в экологию">
      <RequestStatus loading={eco.loading || transfers.loading} error={eco.error || transfers.error} retry={() => {
        if (eco.error) eco.reload();
        if (transfers.error) transfers.reload();
      }} />
      {eco.data && !transfers.loading && <ProfileEco stats={eco.data} transfers={transfers.data} />}
    </section>}
  </>;
}
