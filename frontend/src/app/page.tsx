import Link from "next/link";

export default function Home() {
  return <section>
    <h1>Добрый Шкаф</h1>
    <p>Каталог вещей для бесплатной передачи.</p>
    <Link className="button" href="/items">Открыть каталог</Link>
  </section>;
}
