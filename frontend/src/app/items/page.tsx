import ItemList from "@/components/ItemList";

export default async function Catalog({ searchParams }: { searchParams: Promise<{ q?: string | string[] }> }) {
  const { q } = await searchParams;
  const search = typeof q === "string" ? q.trim() : "";
  return <ItemList key={search} search={search} />;
}
