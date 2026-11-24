import { EditItem } from "@/components/ItemForm";
import { PrivatePage } from "@/components/Session";

export default async function EditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PrivatePage><EditItem id={id} /></PrivatePage>;
}
