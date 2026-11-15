import { PrivatePage } from "@/components/Session";
import { TransferDetails } from "@/components/Transfers";

export default async function TransferPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PrivatePage><TransferDetails key={id} id={id} /></PrivatePage>;
}
