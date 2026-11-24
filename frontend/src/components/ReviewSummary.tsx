"use client";

import { useState } from "react";
import { deliveryMethods } from "@/lib/deliveryMethods";
import type { Review } from "@/lib/types";
import ProfileLink from "./ProfileLink";

const reviewDate = new Intl.DateTimeFormat("ru-RU", { day: "numeric", month: "long", year: "numeric" });
const reviewCount = new Intl.PluralRules("ru-RU");
const reviewWords: Record<string, string> = { one: "отзыв", few: "отзыва", many: "отзывов", other: "отзыва" };

export default function ReviewSummary({ rating, reviews }: { rating?: number | null; reviews: Review[] }) {
  const [sort, setSort] = useState("recent");
  const sorted = [...reviews].sort((a, b) => (sort === "rating" ? b.rating - a.rating : 0)
    || Date.parse(b.createdAt) - Date.parse(a.createdAt));

  return <>
    <label className="profile-sort">Сортировка
      <select value={sort} onChange={(event) => setSort(event.target.value)}>
        <option value="recent">Сначала новые</option>
        <option value="rating">По оценке</option>
      </select>
    </label>
    <div className="review-summary">
      <div className="panel review-overview">
        <h2>Общая оценка</h2>
        <p className="profile-rating-value">{rating?.toFixed(1) ?? "—"} <img src="/design/review-summary-star.svg" alt="" /></p>
        <p className="review-count">{reviews.length === 20
          ? "Распределение по последним 20 отзывам"
          : `Показано: ${reviews.length} ${reviewWords[reviewCount.select(reviews.length)]}`}</p>
        {reviews.length > 0 && <div className="review-distribution">
          {[5, 4, 3, 2, 1].map((value) => {
            const share = Math.round(reviews.filter((review) => review.rating === value).length / reviews.length * 100);
            return <div key={value}>
              <span>{value} ★</span>
              <progress value={share} max={100} aria-label={`Доля показанных отзывов с оценкой ${value}`} />
              <span>{share}%</span>
            </div>;
          })}
        </div>}
      </div>
      <ul className="activity-list">
        {reviews.length === 0 && <li className="panel">Отзывов пока нет.</li>}
        {sorted.map((review) => <li className="panel profile-review-card" key={review.id}>
          <div className="review-author">
            <span aria-hidden="true" />
            <div>
              {review.authorId ? <ProfileLink id={review.authorId} fallback="Автор отзыва" /> : <span>Автор отзыва</span>}
              <time dateTime={review.createdAt}>{reviewDate.format(new Date(review.createdAt))}</time>
            </div>
          </div>
          <div className="review-content">
            <div className="review-card-heading">
              <div className="review-score" role="img" aria-label={review.rating + " из 5"}>
                {[1, 2, 3, 4, 5].map((value) => <img key={value} className={value > review.rating ? "review-star-empty" : undefined}
                  src="/design/review-star.svg" alt="" />)}
              </div>
              {review.deliveryMethod && <span className="badge review-delivery-badge">{deliveryMethods[review.deliveryMethod].label}</span>}
            </div>
            {review.text && <p>{review.text}</p>}
          </div>
        </li>)}
      </ul>
    </div>
  </>;
}
