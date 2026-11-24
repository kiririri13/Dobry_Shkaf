import type { DeliveryMethod } from "./types";

export const deliveryMethods = {
  EXCHANGE: { label: "Обмен", icon: "review-repeat.png" },
  FREE: { label: "Безвозмездно", icon: "review-gift.png" },
} satisfies Record<DeliveryMethod, { label: string; icon: string }>;
