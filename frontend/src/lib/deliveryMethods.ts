import type { DeliveryMethod } from "./types";

export const deliveryMethods = {
  EXCHANGE: { label: "Обмен", icon: "review-repeat.svg" },
  FREE: { label: "Безвозмездно", icon: "review-gift.svg" },
} satisfies Record<DeliveryMethod, { label: string; icon: string }>;
