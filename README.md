# Nanas' Kitchens

10 mil yaricapinda ev mutfaklari icin kultur-temali yemek pazari.
BMAD dokumanlari `docs/` altinda; kod, oradaki hikayeleri uygular.

Backend Java'ya tasindi (strangler migration): `apps/api-java` (Spring Boot 4 + Spring AI)
ana API'dir (:8080) — auth, kitchens, inventory, orders ve AI chat agent'i dahil. Frontend
(Next.js, `apps/web`) ve MCP sunucusu yalnizca :8080 ile konusur; eski NestJS API'si
(:3001) kalan hikayeler (menu CRUD, teslimat, Stripe) tasinana kadar referans olarak durur.
Detay: `apps/api-java/README.md`.

## Calistirma
```bash
cp .env.example .env
docker compose up -d          # PostGIS + Redis
pnpm install
pnpm --filter api prisma:generate
pnpm --filter api prisma:migrate:dev
pnpm dev                      # eski api :3001, web :3000, mcp :3002
(cd apps/api-java && ./mvnw spring-boot:run)   # yeni Java API :8080
```

## Hizli test
```bash
curl localhost:8080/health
curl -X POST localhost:8080/auth/register -H 'content-type: application/json' \
  -d '{"email":"ayse@example.com","password":"sifre1234","role":"seller"}'
curl 'localhost:8080/kitchens/search?lat=37.788&lng=-122.4075'
```

## Durum (BMAD hikayeleri)
- [x] 1.1 Iskelet + CI        - [x] 1.2 Auth & roller
- [x] 1.3 Mutfak profili+geo  - [x] 1.4 Uygunluk beyani
- [x] 2.3 Atomik envanter     - [~] 3.3 Siparis cekirdegi (Stripe: Story 3.4)
- [x] 5.1 MCP sunucusu        - [ ] 2.1/2.2 Menu CRUD, 4.x teslimat, 5.2 agent, 6.x, 7.x
