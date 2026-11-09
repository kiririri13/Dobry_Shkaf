export type User = { id: string; name: string; email: string; role: string };
export type Category = { id: string; name: string; sizeRequired: boolean };
export type District = { id: string; name: string; city: string };
export type Condition = { code: string; label: string };
export type Photo = { id: string; url: string; sortOrder: number };

export type Item = {
  id: string;
  title: string;
  description: string | null;
  condition: string | null;
  size: string | null;
  status: "DRAFT" | "PUBLISHED" | "RESERVED" | "TRANSFERRED" | "CLOSED";
  category: Category | null;
  district: District | null;
  owner: { id: string; name: string };
  photos: Photo[];
};

export type ItemPage = { items: Item[]; page: number; totalPages: number };
