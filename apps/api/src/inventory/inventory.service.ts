import { ConflictException, Injectable } from "@nestjs/common";
import { Prisma } from "@prisma/client";
import { PrismaService } from "../prisma/prisma.service";

/**
 * NFR1 / FR8: Asla oku-sonra-yaz YOK. Tek kosullu UPDATE:
 *   UPDATE "MenuItem" SET "portionsRemaining" = "portionsRemaining" - qty
 *   WHERE id = :id AND "portionsRemaining" >= qty
 * rowcount 0 ise PORTIONS_CONFLICT. Es zamanli siparisler asla eksiye dusuremez.
 */
@Injectable()
export class InventoryService {
  constructor(private prisma: PrismaService) {}

  async decrement(tx: Prisma.TransactionClient, menuItemId: string, qty: number): Promise<void> {
    const updated = await tx.$executeRaw`
      UPDATE "MenuItem"
      SET "portionsRemaining" = "portionsRemaining" - ${qty}
      WHERE id = ${menuItemId} AND "portionsRemaining" >= ${qty}`;
    if (updated === 0) {
      throw new ConflictException({ code: "PORTIONS_CONFLICT", menuItemId });
    }
  }

  async restore(tx: Prisma.TransactionClient, menuItemId: string, qty: number): Promise<void> {
    await tx.$executeRaw`
      UPDATE "MenuItem"
      SET "portionsRemaining" = LEAST("portionsRemaining" + ${qty}, "portionsTotal")
      WHERE id = ${menuItemId}`;
  }

  async remaining(menuItemIds: string[]) {
    return this.prisma.menuItem.findMany({
      where: { id: { in: menuItemIds } },
      select: { id: true, portionsRemaining: true, portionsTotal: true },
    });
  }
}
