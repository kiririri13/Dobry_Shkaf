import PublicProfile from "@/components/PublicProfile";

export default async function PublicProfilePage({ params, searchParams }: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ section?: string }>;
}) {
  const [{ id }, { section }] = await Promise.all([params, searchParams]);
  const index = ["items", "history", "reviews", "eco"].indexOf(section ?? "");
  return <PublicProfile key={id + ":" + index} id={id} initialSection={index < 0 ? 2 : index} />;
}
