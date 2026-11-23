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

export type ItemPage = { items: Item[]; page: number; totalPages: number; totalElements: number };

type Participant = { id: string; name: string };
type ItemSummary = { id: string; title: string; photoUrl: string | null };

export type ChatMessage = {
  id: string;
  senderId: string;
  seq: number;
  text: string;
  sentAt: string;
  readAt: string | null;
};

export type Chat = {
  id: string;
  item: ItemSummary;
  counterpart: Participant;
  lastMessage: ChatMessage | null;
  unreadCount: number;
};

export type MessagePage = { messages: ChatMessage[]; hasMore: boolean };

export type TransferAction = "SELECT" | "CONFIRM" | "CANCEL" | "ACCEPT_CANCELLATION" | "DECLINE_CANCELLATION";
export type TransferStatus = "REQUESTED" | "RESERVED" | "AWAITING_CONFIRMATION" | "CANCELLATION_REQUESTED" | "COMPLETED" | "REJECTED" | "CANCELLED";
export type Transfer = {
  id: string;
  item: ItemSummary;
  giver: Participant;
  receiver: Participant;
  chatId: string;
  status: TransferStatus;
  myRole: "GIVER" | "RECEIVER";
  availableActions: TransferAction[];
};

export type TransferEvent = { id: string; actorId: string; toStatus: TransferStatus; createdAt: string };
export type Favorites = { items: Item[]; points: Point[] };
export type Review = { id: string; rating: number; text: string | null; createdAt: string };
export type PointType = "COLLECTION" | "EXCHANGE";
export type PointStatus = "ACTIVE" | "TEMPORARILY_CLOSED" | "ARCHIVED";

export type Point = {
  id: string;
  name: string;
  type: PointType;
  status: PointStatus;
  organization: { name: string };
  district: District;
  address: string;
  latitude: number;
  longitude: number;
  hours: string;
  contacts: string;
  categories: Category[];
  conditions: string;
  receivingConditions: string | null;
  source: string;
  verifiedAt: string;
  temporarilyClosed: boolean;
};
