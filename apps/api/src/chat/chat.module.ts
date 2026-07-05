import { Module } from "@nestjs/common";
import { AgentService } from "./agent.service";
import { ChatController } from "./chat.controller";

@Module({ controllers: [ChatController], providers: [AgentService] })
export class ChatModule {}
