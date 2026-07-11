/** Client-side cart (front-end-spec.md Cart & Checkout, FR7). There is no cart API — a
 * cart is one kitchen's items held in localStorage until checkout POSTs to /orders. */

export interface CartLine {
  menuItemId: string;
  dishName: string;
  priceCents: number;
  photo: string | null;
  qty: number;
}

export interface Cart {
  kitchenId: string;
  kitchenName: string;
  menuDayId: string;
  menuDate: string; // YYYY-MM-DD, used to build the ready-slot datetime
  lines: CartLine[];
}

const KEY = "cart";
const EVENT = "cart-changed";

export function getCart(): Cart | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Cart) : null;
  } catch {
    return null;
  }
}

function save(cart: Cart | null) {
  if (cart && cart.lines.length > 0) {
    localStorage.setItem(KEY, JSON.stringify(cart));
  } else {
    localStorage.removeItem(KEY);
  }
  window.dispatchEvent(new Event(EVENT));
}

/** Adds/increments a line. Switching kitchens (or menu days) starts a fresh cart —
 * an order can only contain items from one kitchen (Story 3.3 AC1). */
export function addLine(
  ctx: { kitchenId: string; kitchenName: string; menuDayId: string; menuDate: string },
  line: CartLine,
) {
  const current = getCart();
  const sameCart =
    current && current.kitchenId === ctx.kitchenId && current.menuDayId === ctx.menuDayId;
  const cart: Cart = sameCart ? current! : { ...ctx, lines: [] };
  const existing = cart.lines.find((l) => l.menuItemId === line.menuItemId);
  if (existing) {
    existing.qty += line.qty;
  } else {
    cart.lines.push({ ...line });
  }
  save(cart);
}

export function setQty(menuItemId: string, qty: number) {
  const cart = getCart();
  if (!cart) return;
  const line = cart.lines.find((l) => l.menuItemId === menuItemId);
  if (!line) return;
  if (qty <= 0) {
    cart.lines = cart.lines.filter((l) => l.menuItemId !== menuItemId);
  } else {
    line.qty = qty;
  }
  save(cart);
}

export function clearCart() {
  save(null);
}

export function cartCount(cart: Cart | null = getCart()): number {
  return cart ? cart.lines.reduce((n, l) => n + l.qty, 0) : 0;
}

export function cartSubtotalCents(cart: Cart | null = getCart()): number {
  return cart ? cart.lines.reduce((n, l) => n + l.priceCents * l.qty, 0) : 0;
}

/** Subscribes to cart changes (this tab + other tabs); returns an unsubscribe fn. */
export function subscribeCart(cb: () => void): () => void {
  window.addEventListener(EVENT, cb);
  window.addEventListener("storage", cb);
  return () => {
    window.removeEventListener(EVENT, cb);
    window.removeEventListener("storage", cb);
  };
}

export const money = (cents: number) => `$${(cents / 100).toFixed(2)}`;
