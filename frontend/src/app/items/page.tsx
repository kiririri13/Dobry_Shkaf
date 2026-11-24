import ItemList from "@/components/ItemList";

export default async function Catalog({ searchParams }: { searchParams: Promise<{ q?: string | string[]; categoryId?: string | string[] }> }) {
  const { q, categoryId } = await searchParams;
  const search = typeof q === "string" ? q.trim() : "";
  const category = typeof categoryId === "string" ? categoryId : "";
  return <ItemList key={search + ":" + category} search={search} category={category} />;
}
