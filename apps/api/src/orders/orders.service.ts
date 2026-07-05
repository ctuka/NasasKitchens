import { BadRequestException, ForbiddenException, Injectable, NotFoundException } from "@nestjs/common";
import { randomUUID } from "node:crypto";
import { PrismaService } from "../prisma/prisma.service";
import { InventoryService } from "../inventory/inventory.service";
import { decryptAddress } from "@culture-eats/core";
import type { CreateOrderInput } from "@culture-eats/core";

const COMMISSION_RATE = 0.15;

@Injectable()
export class OrdersService {
  constructor(private prisma: PrismaService, private inventory: InventoryService) {}

  /**
   * FR15 (sunucu tarafli): confirm !== true ise siparis OLUSTURULMAZ,
   * yalnizca fiyatlandirilmis ozet doner. Agent/MCP ve UI ayni yoldan gecer (NFR3).
   * Anti-halusinasyon: her menuItemId, mutfagin o gunku YAYINLANMIS menusunde olmali.
   */
  async place(buyerId: string, input: CreateOrderInput) {
    const menuDay = await this.prisma.menuDay.findFirst({
      where: { id: input.menuDayId, kitchenId: input.kitchenId, status: "published" },
      include: { items: { include: { dish: true } } },
    });
    if (!menuDay) throw new BadRequestException("MENU_NOT_PUBLISHED");

    const valid = new Map(menuDay.items.map((mi) => [mi.id, mi]));
    for (const it of input.items) {
      if (!valid.has(it.menuItemId)) throw new BadRequestException({ code: "ITEM_NOT_IN_MENU", menuItemId: it.menuItemId });
      if (it.qty < 1) throw new BadRequestException("QTY_INVALID");
    }

    const totalCents = input.items.reduce(
      (sum, it) => sum + valid.get(it.menuItemId)!.dish.priceCents * it.qty, 0);
    const commissionCents = Math.round(totalCents * COMMISSION_RATE);

    const summary = {
      kitchenId: input.kitchenId,
      items: input.items.map((it) => ({
        menuItemId: it.menuItemId,
        dish: valid.get(it.menuItemId)!.dish.name,
        qty: it.qty,
        unitPriceCents: valid.get(it.menuItemId)!.dish.priceCents,
      })),
      readySlot: input.readySlot,
      fulfillment: input.fulfillment,
      totalCents,
    };

    if (input.confirm !== true) {
      return { confirmed: false, summary }; // FR15: once ozet, onay yoksa islem yok
    }

    // Atomik: dusum + siparis tek transaction (Story 2.3 / mimari Workflow 1)
    const order = await this.prisma.$transaction(async (tx) => {
      for (const it of input.items) {
        await this.inventory.decrement(tx, it.menuItemId, it.qty);
      }
      return tx.order.create({
        data: {
          buyerId,
          kitchenId: input.kitchenId,
          menuDayId: input.menuDayId,
          status: "confirmed",
          readySlot: new Date(input.readySlot),
          fulfillment: input.fulfillment,
          totalCents,
          commissionCents,
          idempotencyKey: randomUUID(), // Stripe entegrasyonunda draft id olacak (Story 3.4)
          items: {
            create: input.items.map((it) => ({
              menuItemId: it.menuItemId,
              qty: it.qty,
              unitPriceCents: valid.get(it.menuItemId)!.dish.priceCents,
            })),
          },
        },
        include: { items: true },
      });
    });

    await this.prisma.auditLog.create({
      data: { actor: buyerId, entity: `Order:${order.id}`, action: "create", after: summary as any },
    });
    return { confirmed: true, order: await this.detail(buyerId, order.id) };
  }

  /** FR10: adim adim adres ifsasi — sokak adresi yalnizca onaylanmis pickup siparisinde. */
  async detail(buyerId: string, orderId: string) {
    const order = await this.prisma.order.findUnique({
      where: { id: orderId },
      include: { items: { include: { menuItem: { include: { dish: true } } } }, kitchen: true },
    });
    if (!order) throw new NotFoundException();
    if (order.buyerId !== buyerId) throw new ForbiddenException(); // MCP AC4: alici kapsami
    const { kitchen, ...rest } = order;
    return {
      ...rest,
      kitchenName: kitchen.name,
      pickupAddress:
        order.fulfillment === "pickup" && order.status !== "pending" && order.status !== "cancelled"
          ? decryptAddress(kitchen.addressEncrypted)
          : undefined,
    };
  }

  async cancel(buyerId: string, orderId: string) {
    return this.prisma.$transaction(async (tx) => {
      const order = await tx.order.findUnique({ where: { id: orderId }, include: { items: true } });
      if (!order) throw new NotFoundException();
      if (order.buyerId !== buyerId) throw new ForbiddenException();
      if (["completed", "cancelled"].includes(order.status)) throw new BadRequestException("NOT_CANCELLABLE");
      for (const it of order.items) {
        await this.inventory.restore(tx, it.menuItemId, it.qty); // iade ayni transaction'da (Story 2.3 AC2)
      }
      return tx.order.update({ where: { id: orderId }, data: { status: "cancelled" } });
    });
  }

  status(buyerId: string, orderId: string) { return this.detail(buyerId, orderId); }
}
