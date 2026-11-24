import Link from "next/link";
import { useState } from "react";
import { useData } from "@/lib/useData";
import type { EcoStats, Transfer } from "@/lib/types";

const monthFormat = new Intl.DateTimeFormat("ru-RU", { month: "short" });
const numberFormat = new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 1 });
const percentFormat = new Intl.NumberFormat("ru-RU", { style: "percent", maximumFractionDigits: 1 });
const categoryColors = ["#183f2e", "#2f7958", "#3bac7b", "#97c7a8"];

function kilograms(value: number | null | undefined) {
  return value == null ? "—" : numberFormat.format(value) + " кг";
}

type ItemEco = {
  event: { active: boolean } | null;
  credits: { lineId: string; co2eKg: number | null; counted: boolean }[];
  recalculations: { lineId: string; co2eKg: number; coefficientVersion: number }[];
};

function EcoCategories({ categories }: { categories?: EcoStats["byCategory"] }) {
  const rows = (categories ?? []).filter(({ quantity }) => quantity > 0).sort((a, b) => b.quantity - a.quantity);
  const total = rows.reduce((sum, row) => sum + row.quantity, 0);
  const groups = rows.length <= categoryColors.length ? rows : [
    ...rows.slice(0, 3),
    { code: "other", name: "Другие категории", quantity: rows.slice(3).reduce((sum, row) => sum + row.quantity, 0) },
  ];
  let offset = 0;
  const segments = groups.map((row, index) => {
    const start = offset;
    const share = row.quantity / total;
    offset += share * 100;
    return { ...row, share, start, end: offset, color: categoryColors[index] };
  });
  const gradient = total ? `conic-gradient(${segments.map(({ start, end, color }) => `${color} ${start}% ${end}%`).join(", ")})` : undefined;

  return <section className="eco-categories">
    <h2>Вклад по категориям</h2>
    <div className="eco-category-summary">
      <div className="eco-ring" style={{ background: gradient }}>
        <span>{categories ? numberFormat.format(total) : "—"}<small>шт.</small></span>
      </div>
      {segments.length > 0 ? <ul className="activity-list eco-category-list">
        {segments.map(({ code, name, share, color, quantity }) => <li key={code}>
          <span className="eco-category-dot" style={{ background: color }} />
          <span>{name}</span><span title={`${numberFormat.format(quantity)} шт.`}>{percentFormat.format(share)}</span>
        </li>)}
      </ul> : <p>Нет данных по категориям</p>}
    </div>
  </section>;
}

function EcoActivity({ transfer }: { transfer: Transfer & { completedAt: string } }) {
  const { id, item, completedAt, myRole } = transfer;
  const action = item.deliveryMethod === "EXCHANGE" ? "Обмен: " : myRole === "GIVER" ? "Передано: " : "Получено: ";
  const eco = useData<ItemEco>(myRole === "GIVER" ? "/items/" + encodeURIComponent(item.id) + "/eco" : "").data;
  const estimates = eco?.event?.active ? eco.credits.filter((credit) => credit.counted).map((credit) => {
    const latest = eco.recalculations.filter((entry) => entry.lineId === credit.lineId)
      .sort((a, b) => b.coefficientVersion - a.coefficientVersion)[0];
    return latest?.co2eKg ?? credit.co2eKg;
  }).filter((value) => value !== null) : [];
  const co2 = estimates.length ? estimates.reduce((total, value) => total + value, 0) : null;

  return <article className="card eco-activity">
    <div className="eco-activity-photo">{item.photoUrl && <img src={item.photoUrl} alt="" />}</div>
    <div className="eco-activity-details">
      <Link href={"/profile/transfers/" + encodeURIComponent(id)}>{action}{item.title}</Link>
      <time dateTime={completedAt}>{new Date(completedAt).toLocaleDateString("ru-RU")}</time>
      {co2 !== null && <span className="badge">{kilograms(co2)} CO₂</span>}
    </div>
  </article>;
}

export default function ProfileEco({ stats, completedTransfers, transfers }: {
  stats?: EcoStats;
  completedTransfers?: number;
  transfers?: Transfer[];
}) {
  const [period, setPeriod] = useState("year");
  const recent = (transfers ?? []).filter((entry): entry is Transfer & { completedAt: string } =>
    entry.status === "COMPLETED" && Boolean(entry.completedAt))
    .sort((a, b) => Date.parse(b.completedAt) - Date.parse(a.completedAt));
  const now = new Date();
  const months = Array.from({ length: period === "year" ? 12 : 6 }, (_, i) => {
    const start = new Date(now.getFullYear(), period === "year" ? i : now.getMonth() - 5 + i, 1);
    const end = new Date(start.getFullYear(), start.getMonth() + 1, 1);
    const total = recent.filter(({ completedAt }) => {
      const time = Date.parse(completedAt);
      return time >= start.getTime() && time < end.getTime();
    }).length;
    return { label: monthFormat.format(start).replace(/\.$/, ""), total };
  });
  const step = Math.max(5, Math.ceil(Math.max(...months.map(({ total }) => total)) / 3));
  const co2 = stats?.contribution.co2eKg;
  const given = recent.filter((entry) => entry.myRole === "GIVER");
  const exchanges = given.filter(({ item }) => item.deliveryMethod === "EXCHANGE").length;
  const metrics = transfers ? [
    ["home-package.svg", "Передано", given.length - exchanges],
    ["review-repeat.png", "Обменяно", exchanges],
    ["home-truck.svg", "Сокращено отходов", kilograms(stats?.contribution.weightKg)],
  ] as const : [["home-package.svg", "Завершенные передачи", stats?.completedTransfers ?? completedTransfers ?? "—"]] as const;

  return <div className="profile-eco-layout">
    <section className="panel eco-totals">
      <h2>CO₂ сэкономлено</h2>
      <p>{kilograms(co2)}</p>
      {stats?.estimateNotice && <small>{stats.estimateNotice}</small>}
    </section>
    <dl className="eco-counts">
      {metrics.map(([icon, label, value]) => <div key={label}>
        <img src={"/design/" + icon} alt="" />
        <dt>{label}</dt><dd>{value}</dd>
      </div>)}
    </dl>
    <section className="panel eco-trend">
      <header>
        <h2>Динамика вклада</h2>
        {transfers && <select aria-label="Период графика" value={period} onChange={(event) => setPeriod(event.target.value)}>
          <option value="year">За год</option>
          <option value="half-year">За полгода</option>
        </select>}
      </header>
      {transfers ? <div className="eco-plot">
        <div className="eco-scale" aria-hidden="true">{[3, 2, 1, 0].map((line) => <span key={line}>{line * step}</span>)}</div>
        <ul className="eco-chart" aria-label="Число завершённых передач по месяцам" style={{ gridTemplateColumns: `repeat(${months.length}, minmax(0, 1fr))` }}>
          {months.map(({ label, total }) => <li key={label} aria-label={`${label}: ${total}`}>
            <div className="eco-bar"><span style={{ height: `${total / (3 * step) * 100}%` }} /></div>
            <span>{label}</span>
          </li>)}
        </ul>
      </div> : <p>Данные по месяцам доступны в личном профиле.</p>}
    </section>
    <EcoCategories categories={stats?.byCategory} />
    {transfers && <section className="eco-recent">
      <h2>Последняя активность</h2>
      {recent.length ? <div className="eco-activities">
        {recent.slice(0, 3).map((transfer) => <EcoActivity key={transfer.id} transfer={transfer} />)}
      </div> : <p>Завершённых передач пока нет.</p>}
    </section>}
  </div>;
}
