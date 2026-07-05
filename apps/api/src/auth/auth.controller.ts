import { Body, Controller, Post } from "@nestjs/common";
import { IsEmail, IsIn, IsString, MinLength } from "class-validator";
import { AuthService } from "./auth.service";

class RegisterDto {
  @IsEmail() email!: string;
  @IsString() @MinLength(8) password!: string;
  @IsIn(["buyer", "seller"]) role!: "buyer" | "seller";
}
class LoginDto {
  @IsEmail() email!: string;
  @IsString() password!: string;
}
class RefreshDto { @IsString() refreshToken!: string; }

@Controller("auth")
export class AuthController {
  constructor(private auth: AuthService) {}
  @Post("register") register(@Body() dto: RegisterDto) { return this.auth.register(dto.email, dto.password, dto.role); }
  @Post("login") login(@Body() dto: LoginDto) { return this.auth.login(dto.email, dto.password); }
  @Post("refresh") refresh(@Body() dto: RefreshDto) { return this.auth.refresh(dto.refreshToken); }
}
