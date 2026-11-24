"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { api, ApiError, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import { deliveryMethods } from "@/lib/deliveryMethods";
import type { Profile, Review, Transfer } from "@/lib/types";
import RequestStatus from "./RequestStatus";
import ReviewSummary from "./ReviewSummary";
import { profileSections } from "./ProfileTabs";
import UnderlineTabs from "./UnderlineTabs";

const ratings = [1, 2, 3, 4, 5];
const commentLimit = 100;

export default function ReviewForm({ id }: { id: string }) {
  const path = "/transfers/" + encodeURIComponent(id);
  const transfer = useData<Transfer>(path);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const data = transfer.data;
  const method = data?.item.deliveryMethod ? deliveryMethods[data.item.deliveryMethod] : undefined;
  const recipient = data && (data.myRole === "GIVER" ? data.receiver : data.giver);
  const profilePath = recipient ? "/profiles/" + encodeURIComponent(recipient.id) : "";
  const profile = useData<Profile>(profilePath);
  const reviews = useData<Review[]>(profilePath ? profilePath + "/reviews" : "");
  const back = "/profile" + path;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !rating) return;
    setBusy(true);
    setError("");
    try {
      await api(path + "/reviews", {
        method: "POST",
        json: { rating, text: comment.trim() },
      });
      setNotice("Отзыв опубликован.");
      profile.reload();
      reviews.reload();
    } catch (failure) {
      if (failure instanceof ApiError && failure.code === "REVIEW_EXISTS") setNotice("Вы уже оставили отзыв по этой передаче.");
      else setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <section className="review-page">
    <p className="breadcrumbs"><Link href={back}>К передаче</Link> / Написание отзыва</p>
    <RequestStatus loading={transfer.loading} error={transfer.error} retry={transfer.reload} />
    {data && recipient && <>
      <div className="profile">
        <div className="profile-cover" />
        <div className="profile-info">
          <div className="profile-avatar" aria-hidden="true">{profile.data?.avatarUrl && <img src={profile.data.avatarUrl} alt="" />}</div>
          <div className="profile-name"><h1>{recipient.name}</h1>
            {profile.data?.district && <p className="profile-district"><img src="/design/pin.svg" alt="" />
              {profile.data.district.name}, {profile.data.district.city}
            </p>}
          </div>
        </div>
        <dl className="profile-stats">
          <div><img src="/design/profile-listings.svg" alt="" /><dt>Выложено вещей</dt><dd>—</dd></div>
          <div><img src="/design/profile-given.svg" alt="" /><dt>Передачи</dt><dd>{profile.data?.completedTransfers ?? "—"}</dd></div>
          <div><img src="/design/profile-rating.svg" alt="" /><dt>Рейтинг</dt><dd>{profile.data?.rating?.toFixed(1) ?? "—"}</dd></div>
          <div><img src="/design/profile-reviews.svg" alt="" /><dt>Отзывов</dt><dd>{reviews.data?.length === 20 ? "20+" : reviews.data?.length ?? "—"}</dd></div>
        </dl>
      </div>
      <UnderlineTabs label="Разделы профиля" className="profile-sections review-form-tabs">
        {["items", "history", "reviews", "eco"].map((section, index) => <Link
          key={section} href={profilePath + "?section=" + section} aria-current={index === 2 ? "page" : undefined}>
          {index === 0 ? "Объявления пользователя" : profileSections[index]}
        </Link>)}
      </UnderlineTabs>
      <RequestStatus loading={profile.loading || reviews.loading} error={profile.error || reviews.error}
        retry={() => { profile.reload(); reviews.reload(); }} />
      {reviews.data && <ReviewSummary rating={profile.data?.rating} reviews={reviews.data} />}
      {data.status !== "COMPLETED" ? <p>Отзыв можно оставить после завершения передачи.</p>
        : notice ? <p role="status">{notice}</p>
        : <form className="review-compose" onSubmit={submit}>
          <fieldset className="panel review-fields" disabled={busy}>
            <fieldset className="review-rating">
              <legend>Оценка</legend>
              <div className="review-stars">
                {ratings.map((value) => <label key={value}>
                  <input type="radio" name="rating" value={value} aria-label={value + " из 5"} required
                    checked={rating === value} onChange={() => setRating(value)} />
                  <img src="/design/review-star.svg" className={value > rating ? "review-star-empty" : undefined} alt="" />
                </label>)}
                <output>{rating.toFixed(1)}</output>
              </div>
            </fieldset>
            <div className="review-comment">
              <label htmlFor="review-text">Ваш отзыв</label>
              <div className="review-input">
                <textarea id="review-text" name="text" rows={4} maxLength={commentLimit} value={comment}
                  aria-describedby="review-counter" onChange={(event) => setComment(event.target.value)}
                  placeholder="Поделитесь впечатлением о взаимодействии с пользователем..." />
                <span className="review-counter" id="review-counter">{comment.length}/{commentLimit}</span>
              </div>
            </div>
            {method && <dl className="review-method">
              <dt>Способ передачи</dt>
              <dd><img src={"/design/" + method.icon} alt="" />{method.label}</dd>
            </dl>}
          </fieldset>
          <div className="review-actions">
            <Link className="button secondary" href={back}>Отмена</Link>
            <button disabled={busy}>{busy ? "Отправляем…" : "Опубликовать"}</button>
          </div>
        </form>}
    </>}
    <RequestStatus error={error} />
  </section>;
}
