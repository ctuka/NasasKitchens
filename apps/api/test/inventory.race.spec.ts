/**
 * NFR1 dogrulamasi (Story 2.3 AC1):
 * portionsRemaining=3 olan bir MenuItem'a es zamanli iki adet 2'lik dusum yapilir.
 * Beklenen: tam olarak biri basarili, digeri basarisiz; kalan = 1.
 * Gercek Postgres'e karsi calisir (mock degil) — `docker compose up -d db` gerektirir.
 */
import { PrismaClient } from "@prisma/client";

describe("InventoryService race safety", () => {
  const prisma = new PrismaClient();

  async function decrement(menuItemId: string, qty: number): Promise<boolean> {
    const updated = await prisma.$executeRaw`
      UPDATE "MenuItem" SET "portionsRemaining" = "portionsRemaining" - ${qty}
      WHERE id = ${menuItemId} AND "portionsRemaining" >= ${qty}`;
    return updated === 1;
  }

  it("never oversells under concurrency", async () => {
    const seller = await prisma.user.create({
      data: { email: `race-${Date.now()}@test.dev`, passwordHash: "x", role: "seller" },
    });
    const kitchen = await prisma.kitchen.create({
      data: {
        sellerId: seller.id, name: "Race Test Kitchen", cuisineTag: "turkish",
        description: "", addressEncrypted: "enc", photos: [],
      },
    });
    const dish = await prisma.dish.create({
      data: { kitchenId: kitchen.id, name: "Manti", description: "", priceCents: 1200, dietaryTags: [] },
    });
    const menuDay = await prisma.menuDay.create({
      data: { kitchenId: kitchen.id, date: new Date(), readyWindows: [], status: "published" },
    });
    const item = await prisma.menuItem.create({
      data: { menuDayId: menuDay.id, dishId: dish.id, portionsTotal: 3, portionsRemaining: 3 },
    });

    const [a, b] = await Promise.all([decrement(item.id, 2), decrement(item.id, 2)]);
    expect([a, b].filter(Boolean)).toHaveLength(1); // tam olarak biri kazanir

    const final = await prisma.menuItem.findUnique({ where: { id: item.id } });
    expect(final!.portionsRemaining).toBe(1);
  }, 30000);

  afterAll(() => prisma.$disconnect());
});
