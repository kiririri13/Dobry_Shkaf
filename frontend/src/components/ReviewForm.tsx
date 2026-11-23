"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { api, ApiError, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { District, Review, Transfer } from "@/lib/types";
import RequestStatus from "./RequestStatus";

const ratings = [1, 2, 3, 4, 5];

export default function ReviewForm({ id }: { id: string }) {
  const path = "/transfers/" + encodeURIComponent(id);
  const transfer = useData<Transfer>(path);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const data = transfer.data;
  const recipient = data && (data.myRole === "GIVER" ? data.receiver : data.giver);
  const profilePath = recipient ? "/profiles/" + encodeURIComponent(recipient.id) : "";
  const profile = useData<{ district: District | null; rating: number | null; completedTransfers: number }>(profilePath);
  const reviews = useData<Review[]>(profilePath ? profilePath + "/reviews" : "");
  const back = "/profile" + path;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
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
          <div className="profile-avatar" aria-hidden="true">{recipient.name.trim().charAt(0).toUpperCase()}</div>
          <div className="profile-name"><h1>{recipient.name}</h1><p>{profile.data?.district?.name}</p></div>
        </div>
        <dl className="review-stats">
          <div><dt>Передачи</dt><dd>{profile.data?.completedTransfers ?? "—"}</dd></div>
          <div><dt>Рейтинг</dt><dd>{profile.data?.rating?.toFixed(1) ?? "—"}</dd></div>
        </dl>
      </div>
      <h2 className="review-heading">Отзывы</h2>
      <RequestStatus loading={profile.loading || reviews.loading} error={profile.error || reviews.error}
        retry={() => { profile.reload(); reviews.reload(); }} />
      {reviews.data && <div className="review-summary">
        <div className="panel">
          <h2>Общая оценка</h2><p className="profile-rating-value">{profile.data?.rating?.toFixed(1) ?? "—"}</p>
        </div>
        <ul className="activity-list">
          {reviews.data.length === 0 && <li className="panel">Отзывов пока нет.</li>}
          {reviews.data.map((review) => <li className="panel" key={review.id}>
            <p className="review-score" aria-label={review.rating + " из 5"}>{"★".repeat(review.rating)}{"☆".repeat(5 - review.rating)}</p>
            <time dateTime={review.createdAt}>{new Date(review.createdAt).toLocaleDateString("ru-RU")}</time>
            {review.text && <p>{review.text}</p>}
          </li>)}
        </ul>
      </div>}
      {data.status !== "COMPLETED" ? <p>Отзыв можно оставить после завершения передачи.</p>
        : notice ? <p role="status">{notice}</p>
        : <form onSubmit={submit}>
          <fieldset className="panel review-fields" disabled={busy}>
            <fieldset>
              <legend>Оценка</legend>
              <div className="review-stars">
                {ratings.map((value) => <label key={value}>
                  <input type="radio" name="rating" value={value} aria-label={value + " из 5"} required
                    checked={rating === value} onChange={() => setRating(value)} />
                  <span aria-hidden="true" className={value <= rating ? "selected" : ""}>★</span>
                </label>)}
                <output>{rating.toFixed(1)}</output>
              </div>
            </fieldset>
            <label>Ваш отзыв
              <textarea name="text" rows={4} maxLength={100} value={comment}
                onChange={(event) => setComment(event.target.value)}
                placeholder="Поделитесь впечатлением о взаимодействии с пользователем..." />
              <span className="review-counter">{comment.length}/100</span>
            </label>
            <fieldset disabled title="Способ передачи пока нельзя сохранить">
              <legend>Способ передачи</legend>
              <div className="actions review-method">
                <button type="button" className="secondary"><img src="/design/review-repeat.png" alt="" />Обменял</button>
                <button type="button" className="secondary"><img src="/design/review-gift.png" alt="" />Забрал</button>
              </div>
            </fieldset>
          </fieldset>
          <div className="section-head review-actions">
            <Link className="button secondary" href={back}>Отмена</Link>
            <button disabled={busy}>{busy ? "Отправляем…" : "Опубликовать"}</button>
          </div>
        </form>}
    </>}
    <RequestStatus error={error} />
  </section>;
}
