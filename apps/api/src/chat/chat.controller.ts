import { Body, Controller, Post, Req, Res, UseGuards } from "@nestjs/common";
import { AuthGuard } from "@nestjs/passport";
import { IsArray, IsIn, IsString, ValidateNested } from "class-validator";
import { Type } from "class-transformer";
import { Request, Response } from "express";
import { AgentService } from "./agent.service";
import Anthropic from "@anthropic-ai/sdk";

class MessageDto {
  @IsIn(["user", "assistant"]) role!: "user" | "assistant";
  @IsString() content!: string;
}

class ChatDto {
  @IsArray() @ValidateNested({ each: true }) @Type(() => MessageDto) messages!: MessageDto[];
}

@Controller("chat")
export class ChatController {
  constructor(private agent: AgentService) {}

  /**
   * POST /chat/stream
   * Accepts conversation history, streams SSE agent response.
   * Auth: valid buyer JWT required.
   */
  @Post("stream")
  @UseGuards(AuthGuard("jwt"))
  async stream(@Req() req: Request, @Body() dto: ChatDto, @Res() res: Response) {
    const token = (req.headers.authorization ?? "").replace(/^Bearer\s+/i, "");
    const messages: Anthropic.MessageParam[] = dto.messages.map((m) => ({
      role: m.role,
      content: m.content,
    }));
    await this.agent.streamChat(messages, token, res);
  }
}
