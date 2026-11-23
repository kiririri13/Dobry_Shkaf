import { PrivatePage } from "@/components/Session";
import ReviewForm from "@/components/ReviewForm";

export default async function ReviewPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PrivatePage><ReviewForm key={id} id={id} /></PrivatePage>;
}
