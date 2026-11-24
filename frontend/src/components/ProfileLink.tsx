"use client";

import Link from "next/link";
import { useData } from "@/lib/useData";
import type { Profile } from "@/lib/types";

export default function ProfileLink({ id, fallback }: { id: string; fallback: string }) {
  const href = "/profiles/" + encodeURIComponent(id);
  const { data } = useData<Profile>(href);
  return <Link href={href} prefetch={false}>{data?.name || fallback}</Link>;
}
