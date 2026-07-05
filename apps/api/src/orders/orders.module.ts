import { Module } from "@nestjs/common";
import { InventoryModule } from "../inventory/inventory.module";
import { OrdersService } from "./orders.service";
import { OrdersController } from "./orders.controller";

@Module({ imports: [InventoryModule], providers: [OrdersService], controllers: [OrdersController], exports: [OrdersService] })
export class OrdersModule {}
