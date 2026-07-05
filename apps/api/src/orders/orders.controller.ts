import { Body, Controller, Get, Param, Post, Req, UseGuards } from "@nestjs/common";
import { AuthGuard } from "@nestjs/passport";
import { OrdersService } from "./orders.service";
import type { CreateOrderInput } from "@culture-eats/core";

@Controller("orders")
@UseGuards(AuthGuard("jwt"))
export class OrdersController {
  constructor(private orders: OrdersService) {}

  @Post() place(@Req() req: any, @Body() body: CreateOrderInput) {
    return this.orders.place(req.user.userId, body);
  }
  @Get(":id") detail(@Req() req: any, @Param("id") id: string) {
    return this.orders.detail(req.user.userId, id);
  }
  @Post(":id/cancel") cancel(@Req() req: any, @Param("id") id: string) {
    return this.orders.cancel(req.user.userId, id);
  }
}
