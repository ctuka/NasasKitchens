# Story 1.2: Authentication & Roles

**Epic:** 1 — Foundation & Kitchen Onboarding    **Status:** Draft
**Traces:** FR1, FR2, FR20 (role gates), NFR5, NFR10

## Story
As a user, I want to register and sign in with a role (buyer, seller, inspector, admin),
so that each portal only shows me what I'm allowed to do.

## Acceptance Criteria
1. Given a new visitor, When they register with email+password and choose buyer or
   seller, Then a User row is created and a JWT access/refresh pair is issued.
2. Given an inspector or admin role, Then it cannot be self-selected at registration —
   only granted by an admin (seeded admin account exists).
3. Given an expired access token with a valid refresh token, When the client refreshes,
   Then a new pair is issued and the old refresh token is revoked (rotation).
4. Given any request to a role-guarded endpoint with the wrong role, Then 403.
5. All auth mutations are written to AuditLog (NFR10).

## Dev Notes (embedded context)
- Entity: `User(id, role, email, phone, locale)` + passwordHash (argon2), refresh token
  table with rotation.
- Files: `apps/api/src/auth/{auth.module,auth.service,auth.controller,jwt.strategy,
  roles.guard}.ts`, `packages/core/src/entities/user.ts`, migration `0002_users_auth`.
- Endpoints: POST /auth/register, POST /auth/login, POST /auth/refresh, POST /auth/logout.

## Tasks
- [ ] Prisma models User + RefreshToken; argon2 hashing
- [ ] JWT strategy + RolesGuard decorator `@Roles('seller')`
- [ ] Refresh rotation + revocation list in Redis
- [ ] Admin seed script; role-grant endpoint (admin only)
- [ ] AuditLog interceptor wired to auth mutations

## Testing Requirements
- Unit: rotation revokes old token; role guard matrix (4 roles × allow/deny)
- e2e: register→login→refresh→access guarded route
