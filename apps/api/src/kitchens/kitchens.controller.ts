import { Body, Controller, Get, Ip, Param, Post, Query, Req, UseGuards } from "@nestjs/common";
import { AuthGuard } from "@nestjs/passport";
import { IsIn, IsNumber, IsString, MinLength } from "class-validator";
import { Type } from "class-transformer";
import { CUISINE_TAGS } from "@culture-eats/core";
import { KitchensService } from "./kitchens.service";
import { Roles, RolesGuard } from "../auth/roles.guard";

class CreateKitchenDto {
  @IsString() @MinLength(2) name!: string;
  @IsIn(CUISINE_TAGS) cuisineTag!: any;
  @IsString() description!: string;
  @IsString() address!: string;
  @Type(() => Number) @IsNumber() lat!: number;
  @Type(() => Number) @IsNumber() lng!: number;
}

@Controller("kitchens")
export class KitchensController {
  constructor(private kitchens: KitchensService) {}

  @Post()
  @UseGuards(AuthGuard("jwt"), RolesGuard)
  @Roles("seller")
  create(@Req() req: any, @Body() dto: CreateKitchenDto) {
    return this.kitchens.create(req.user.userId, dto);
  }

  @Get("search")
  search(@Query("lat") lat: string, @Query("lng") lng: string, @Query("cuisine") cuisine?: any) {
    return this.kitchens.search(Number(lat), Number(lng), cuisine);
  }

  @Get(":id")
  profile(@Param("id") id: string) {
    return this.kitchens.publicProfile(id);
  }

  @Get(":id/menu")
  menu(@Param("id") id: string, @Query("date") date?: string) {
    return this.kitchens.getPublishedMenu(id, date);
  }

  @Post(":id/attestation")
  @UseGuards(AuthGuard("jwt"), RolesGuard)
  @Roles("seller")
  attest(@Req() req: any, @Param("id") id: string, @Ip() ip: string) {
    return this.kitchens.attest(req.user.userId, id, ip);
  }
}
