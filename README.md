# Nanas' Kitchens

10 mil yaricapinda ev mutfaklari icin kultur-temali yemek pazari.
BMAD dokumanlari `docs/` altinda; kod, oradaki hikayeleri uygular.

Backend Java'ya tasindi (strangler migration): `apps/api-java` (Spring Boot 4 + Spring AI)
ana API'dir (:8080) — auth, kitchens, menuler, inventory, orders, teslimat ve AI chat
agent'i dahil. Frontend (Next.js, `apps/web`) ve MCP sunucusu yalnizca :8080 ile konusur;
eski NestJS API'si (:3001) referans olarak durur. Mobil uygulama Kotlin Multiplatform +
Compose Multiplatform ile (iOS + Android tek codebase, `apps/mobile`) ayni REST/SSE
API'sini kullanacak. Detay: `apps/api-java/README.md`, `docs/front-end-spec.md`.

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
- [x] 2.1/2.2 Menu CRUD + hazir-zaman pencereleri (Java)
- [x] 2.3 Atomik envanter + canli SSE + manuel duzeltme
- [x] 3.3 Siparis cekirdegi + web cart/checkout
- [~] 3.4 Stripe (PaymentIntent + imzali webhook + terkedilen-siparis supurucusu +
      web PaymentElement; PAYMENTS_PROVIDER=mock varsayilan — gercek test anahtariyla
      denenmedi, Connect payout icin satici onboarding bekliyor)
- [x] 4.1 Satici siparis yasam dongusu
- [~] 4.2 Teslimat (mock provider; DoorDash/Grubhub kimlik bilgisi bekliyor)
- [x] 5.1 MCP sunucusu        - [x] 5.2 Konusma agenti
- [~] 4.4 Bildirimler (uygulama ici inbox + web zili; push/email kanali FCM/SES
      kimlik bilgisi bekliyor, NOTIFICATIONS_CHANNEL=log)
- [ ] 6.x guven/topluluk, 7.x mufettis portali, satici web portali
