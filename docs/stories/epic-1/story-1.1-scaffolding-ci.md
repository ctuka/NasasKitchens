# Story 1.1: Project Scaffolding, CI & Environments

**Epic:** 1 — Foundation & Kitchen Onboarding    **Status:** Draft
**Traces:** (enables all FRs), NFR8, NFR10

## Story
As a developer, I want a monorepo with running API, web app, database, and CI, so that
every later story lands in a working, testable system.

## Acceptance Criteria
1. Given a fresh clone, When I run `docker compose up` and `pnpm dev`, Then the NestJS
   API responds on `/health` and the Next.js app renders a placeholder home page.
2. Given a push to any branch, When CI runs, Then lint, typecheck, unit tests, and a
   Postgres-backed integration test all execute and gate merges.
3. Given the monorepo, Then the source tree matches `architecture.md › Source Tree`
   (apps/api, apps/web, packages/core, packages/providers, infra/).

## Dev Notes (embedded context)
- Stack: NestJS 11 / Node 22 / TS 5; Next.js 15; PostgreSQL 16 + PostGIS 3.4; Redis 7.
- Files to create: `package.json` (pnpm workspaces), `apps/api/src/main.ts`,
  `apps/api/src/health/health.controller.ts`, `apps/web/app/page.tsx`,
  `docker-compose.yml` (postgres+postgis image, redis), `.github/workflows/ci.yml`,
  `packages/core/src/index.ts`.
- Enable PostGIS in an initial migration: `CREATE EXTENSION IF NOT EXISTS postgis;`

## Tasks
- [ ] Init pnpm workspace + TS configs
- [ ] NestJS app with /health and config module (env-based)
- [ ] Next.js app shell
- [ ] docker-compose with postgis/postgis:16 and redis:7
- [ ] Migration runner (TypeORM or Prisma — pick Prisma 6) + initial migration
- [ ] GitHub Actions: lint, typecheck, test (spins postgres service)

## Testing Requirements
- API e2e: GET /health → 200 {status:"ok", db:"up"}
- CI proves DB connectivity in pipeline
