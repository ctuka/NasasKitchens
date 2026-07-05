import { Module } from "@nestjs/common";
import { KitchensService } from "./kitchens.service";
import { KitchensController } from "./kitchens.controller";

@Module({ providers: [KitchensService], controllers: [KitchensController], exports: [KitchensService] })
export class KitchensModule {}
