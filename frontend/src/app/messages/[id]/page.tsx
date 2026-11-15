import ChatThread from "@/components/ChatThread";
import { PrivatePage } from "@/components/Session";

export default async function ChatPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PrivatePage><ChatThread key={id} id={id} /></PrivatePage>;
}
