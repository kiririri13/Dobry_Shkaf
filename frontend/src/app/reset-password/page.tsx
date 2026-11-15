import PasswordRecovery from "@/components/PasswordRecovery";

export default async function ResetPassword({ searchParams }: { searchParams: Promise<{ token?: string | string[] }> }) {
  const { token } = await searchParams;
  return <PasswordRecovery token={typeof token === "string" ? token : ""} />;
}
