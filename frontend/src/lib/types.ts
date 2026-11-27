export type User = { id: string; name: string; email: string; phone: string | null; emailConfirmed: boolean; role: string };
export type Category = { id: string; name: string; sizeRequired: boolean };
export type District = { id: string; name: string; city: string };
export type Condition = { code: string; label: string };
export type Photo = { id: string; url: string; sortOrder: number };
export type DeliveryMethod = "EXCHANGE" | "FREE";

export type Item = {
  id: string;
  title: string;
  description: string | null;
  condition: string | null;
  size: string | null;
  deliveryMethod?: DeliveryMethod;
  moderationStatus?: "PENDING" | "REVIEWED" | "HIDDEN";
  status: "DRAFT" | "PUBLISHED" | "RESERVED" | "TRANSFERRED" | "CLOSED";
  category: Category | null;
  district: District | null;
  owner: { id: string; name: string; rating?: number | null };
  photos: Photo[];
  publishedAt?: string | null;
};

export type ItemPage = { items: Item[]; page: number; totalPages: number; totalElements: number };

type Participant = { id: string; name: string };
type ItemSummary = { id: string; title: string; photoUrl: string | null; deliveryMethod?: DeliveryMethod; size?: string | null };

export type ChatMessage = {
  id: string;
  senderId: string;
  seq: number;
  text: string;
  sentAt: string;
  readAt: string | null;
  attachment?: { name: string; sizeBytes: number; url: string } | null;
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
  receiverConfirmed: boolean;
  availableActions: TransferAction[];
  completedAt?: string | null;
  updatedAt?: string;
};

export type TransferEvent = {
  id: string;
  command: TransferAction | "REQUEST" | "AUTO_REJECT";
  actorId: string;
  toStatus: TransferStatus;
  createdAt: string;
};
export type Favorites = { items: Item[]; points: Point[] };
export type Review = { id: string; authorId?: string; rating: number; text: string | null; createdAt: string; deliveryMethod?: DeliveryMethod };
export type Profile = { id: string; name: string; district: District | null; rating: number | null; completedTransfers: number; memberSinceYear?: number; avatarUrl?: string | null; bio?: string | null };
export type EcoStats = {
  completedTransfers: number;
  contribution: { countedItems: number; co2eKg: number | null; weightKg: number | null };
  byCategory?: { code: string; name: string; quantity: number }[];
  estimateNotice?: string | null;
};
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
  photoUrl?: string | null;
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
