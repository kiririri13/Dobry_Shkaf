import MessagesLayout from "@/components/MessagesLayout";

export default async function ChatPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <MessagesLayout id={id} />;
}
