import { PointDetails } from "@/components/Points";

export default async function PointPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PointDetails id={id} />;
}
