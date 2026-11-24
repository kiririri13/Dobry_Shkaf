import AdminDashboard from "@/components/AdminDashboard";
import { PrivatePage } from "@/components/Session";

export default function AdminPage() {
  return <PrivatePage><AdminDashboard /></PrivatePage>;
}
