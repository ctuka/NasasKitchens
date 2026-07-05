import { Body, Controller, Post, UseGuards } from "@nestjs/common";
import { AuthGuard } from "@nestjs/passport";
import { IsArray, IsString } from "class-validator";
import { InventoryService } from "./inventory.service";

class RemainingDto {
  @IsArray() @IsString({ each: true }) menuItemIds!: string[];
}

@Controller("inventory")
export class InventoryController {
  constructor(private inventory: InventoryService) {}

  @Post("remaining")
  @UseGuards(AuthGuard("jwt"))
  remaining(@Body() dto: RemainingDto) {
    return this.inventory.remaining(dto.menuItemIds);
  }
}
