/** Paylasilan alan tipleri - REST DTO'lari ve MCP arac semalari tek kaynaktan turetilir (NFR3). */
export type Role = "buyer" | "seller" | "inspector" | "admin";
export type CuisineTag = "turkish" | "chinese" | "mexican" | "indian" | "italian" | "japanese" | "korean" | "vietnamese" | "lebanese" | "ethiopian" | "persian" | "greek" | "thai" | "other";
export declare const CUISINE_TAGS: CuisineTag[];
export type Fulfillment = "pickup" | "delivery";
export type OrderStatus = "pending" | "confirmed" | "accepted" | "declined" | "preparing" | "ready" | "completed" | "cancelled";
export interface KitchenSearchResult {
    id: string;
    name: string;
    cuisineTag: CuisineTag;
    distanceMiles: number;
    ratingAvg: number | null;
    hygieneScore: number | null;
    portionsLeftToday: number;
}
export interface OrderDraftItem {
    menuItemId: string;
    qty: number;
}
export interface OrderDraft {
    kitchenId: string;
    menuDayId: string;
    items: OrderDraftItem[];
    readySlot: string;
    fulfillment: Fulfillment;
}
/** FR15 - agent onayi sunucu tarafinda da zorunlu */
export interface CreateOrderInput extends OrderDraft {
    confirm: boolean;
}
export declare const SEARCH_RADIUS_MILES = 10;
export declare const METERS_PER_MILE = 1609.344;
