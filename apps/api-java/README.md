# Nanas' Kitchens — Java backend

Spring Boot 4 + Spring AI service replacing the NestJS `apps/api` module by module
(strangler migration). The Next.js frontend (`apps/web`) talks to this service as its
single base URL (`:8080`).

## Stack
- Spring Boot 4.1, Java 21+
- Spring Security (stateless JWT/HS256, Argon2 passwords — hashes and tokens are
  interchangeable with the NestJS service)
- Spring Data JPA + JdbcClient over the **shared, Prisma-managed `culture_eats` database**
  (Prisma owns migrations during the transition; Hibernate never touches the schema)
- Spring AI (Anthropic) for `/chat/stream` (SSE) with tool-calling

## Module status
| Module | Status |
| --- | --- |
| auth (register/login/refresh) | ✅ ported |
| health | ✅ ported |
| kitchens (create/search/profile/menu/attestation, PostGIS) | ✅ ported |
| inventory (atomic conditional decrement, NFR1) | ✅ ported |
| orders (FR15 confirm flow, FR10 address disclosure, cancel/restore) | ✅ ported |
| chat agent (Spring AI tool-calling, all tools native) | ✅ ported |
| menus CRUD (Story 2.1/2.2), delivery, payments (Stripe, 3.4), community, trust | ⏳ not started |

The NestJS `apps/api` is no longer needed for the web flow — everything the frontend and
the MCP server call is served natively here. It stays in the repo as the reference
implementation until the remaining stories are ported.

## Running

```bash
# Postgres with PostGIS + the Prisma schema must exist (docker compose up -d, then
# pnpm --filter api prisma:migrate:deploy if the DB is fresh).
cd apps/api-java
export $(grep -v '^#' ../../.env | xargs)
./mvnw spring-boot:run          # :8080 (JAVA_API_PORT)
```

```bash
curl localhost:8080/health
curl -X POST localhost:8080/auth/login -H 'content-type: application/json' \
  -d '{"email":"buyer@demo.com","password":"demo1234"}'   # seed user
curl 'localhost:8080/kitchens/search?lat=37.788&lng=-122.4075'
```

## Interop notes
- `JWT_SECRET` must be **>= 32 bytes** (jjwt enforces the HS256 spec) and identical for
  both backends; the app fails fast at startup otherwise. Claim shape (`sub`, lowercase
  `role`) matches the NestJS tokens.
- Address encryption (`AddressCrypto`) is byte-compatible with `packages/core/src/crypto.ts`
  (AES-256-GCM, `base64(iv).base64(tag).base64(data)`), so either backend can decrypt
  addresses written by the other.
- Entity/table mapping targets Prisma's quoted camelCase schema
  (`hibernate.globally_quoted_identifiers=true`, lowercase `Role` enum constants,
  app-side uuid generation like Prisma's `@default(uuid())`).
