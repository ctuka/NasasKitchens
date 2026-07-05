# Story 1.3: Kitchen Profile CRUD with Photos & Geocoding

**Epic:** 1 — Foundation & Kitchen Onboarding    **Status:** Draft
**Traces:** FR1, NFR2, NFR5

## Story
As a seller, I want to create my culture-tagged kitchen profile with photos and my home
address, so that buyers can later discover my kitchen and see what I cook.

## Acceptance Criteria
1. Given an authenticated seller, When they submit name, description, one cuisine tag
   (from a controlled list incl. Chinese, Turkish, Mexican, Indian, …), and address,
   Then a Kitchen is created, the address is geocoded to a PostGIS point, and the raw
   address is stored encrypted (NFR5).
2. Given photo uploads (≤10, jpeg/png/webp ≤5 MB), Then they are stored in object
   storage and served via signed URLs on the public profile.
3. Given any unauthenticated visitor, When they open `/kitchens/:id`, Then they see the
   profile (photos, cuisine, description, area-level location like "Augusta, GA — 2.1 mi")
   but never the street address (FR10 future-proofing).
4. Geocoding failure returns a validation error with manual lat/lng entry fallback.

## Dev Notes (embedded context)
- Entity: `Kitchen(id, sellerId, name, cuisineTag, description, photos[],
  address_encrypted, geo geography(Point,4326), complianceAttestedAt, ratingAvg,
  ratingCount)`; GIST index on geo (NFR2).
- Encryption: pgcrypto `pgp_sym_encrypt` via Prisma raw or app-level AES-GCM with KMS key.
- Files: `apps/api/src/kitchens/{kitchens.module,kitchens.service,kitchens.controller}.ts`,
  `packages/providers/src/geocoding/{geocoder.interface,nominatim.provider}.ts`,
  `apps/web/app/kitchens/[id]/page.tsx`, migration `0003_kitchens`.
- Endpoints: POST/PATCH /kitchens (seller, own only), GET /kitchens/:id (public),
  POST /kitchens/:id/photos.

## Tasks
- [ ] Prisma model + PostGIS geography column (raw SQL migration) + GIST index
- [ ] Geocoder provider interface + Nominatim impl + mock for tests
- [ ] Address encryption helper in packages/core
- [ ] Photo upload to S3 with signed URL read
- [ ] Public profile page (SSR) hiding street address
- [ ] Ownership guard: sellers edit only their kitchen

## Testing Requirements
- Integration: geocode→point persisted; radius query returns kitchen at 5 mi, excludes at 12 mi
- Security test: GET profile response contains no street address field
