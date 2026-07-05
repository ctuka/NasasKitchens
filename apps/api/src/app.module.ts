import { Module } from "@nestjs/common";
import { HealthController } from "./health/health.controller";
import { PrismaModule } from "./prisma/prisma.module";
import { AuthModule } from "./auth/auth.module";
import { KitchensModule } from "./kitchens/kitchens.module";
import { InventoryModule } from "./inventory/inventory.module";
import { OrdersModule } from "./orders/orders.module";
import { ChatModule } from "./chat/chat.module";

@Module({
  imports: [PrismaModule, AuthModule, KitchensModule, InventoryModule, OrdersModule, ChatModule],
  controllers: [HealthController],
})
export class AppModule {}
