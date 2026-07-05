import { ForbiddenException, Injectable, UnauthorizedException } from "@nestjs/common";
import { JwtService } from "@nestjs/jwt";
import * as argon2 from "argon2";
import { createHash, randomBytes } from "node:crypto";
import { PrismaService } from "../prisma/prisma.service";
import type { Role } from "@culture-eats/core";

const SELF_SERVE_ROLES: Role[] = ["buyer", "seller"]; // inspector/admin yalnizca admin tarafindan atanir (AC2)

@Injectable()
export class AuthService {
  constructor(private prisma: PrismaService, private jwt: JwtService) {}

  async register(email: string, password: string, role: Role) {
    if (!SELF_SERVE_ROLES.includes(role)) {
      throw new ForbiddenException("ROLE_NOT_SELF_SERVE");
    }
    const passwordHash = await argon2.hash(password);
    const user = await this.prisma.user.create({ data: { email, passwordHash, role } });
    await this.audit(user.id, "User", "register");
    return this.issuePair(user.id, user.role);
  }

  async login(email: string, password: string) {
    const user = await this.prisma.user.findUnique({ where: { email } });
    if (!user || !(await argon2.verify(user.passwordHash, password))) {
      throw new UnauthorizedException("INVALID_CREDENTIALS");
    }
    await this.audit(user.id, "User", "login");
    return this.issuePair(user.id, user.role);
  }

  /** Refresh rotasyonu: eski token tek kullanimlik, dogrulaninca iptal edilir (AC3). */
  async refresh(refreshToken: string) {
    const tokenHash = this.hash(refreshToken);
    const row = await this.prisma.refreshToken.findUnique({ where: { tokenHash }, include: { user: true } });
    if (!row || row.revokedAt || row.expiresAt < new Date()) {
      throw new UnauthorizedException("REFRESH_INVALID");
    }
    await this.prisma.refreshToken.update({ where: { id: row.id }, data: { revokedAt: new Date() } });
    return this.issuePair(row.user.id, row.user.role);
  }

  private async issuePair(sub: string, role: string) {
    const accessToken = await this.jwt.signAsync({ sub, role });
    const refreshToken = randomBytes(48).toString("base64url");
    await this.prisma.refreshToken.create({
      data: { userId: sub, tokenHash: this.hash(refreshToken), expiresAt: new Date(Date.now() + 30 * 864e5) },
    });
    return { accessToken, refreshToken };
  }

  private hash(t: string) { return createHash("sha256").update(t).digest("hex"); }

  private audit(actor: string, entity: string, action: string) {
    return this.prisma.auditLog.create({ data: { actor, entity, action } }); // NFR10
  }
}
