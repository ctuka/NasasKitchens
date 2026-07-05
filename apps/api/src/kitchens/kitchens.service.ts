import { ForbiddenException, Injectable, NotFoundException } from "@nestjs/common";
import { PrismaService } from "../prisma/prisma.service";
import { encryptAddress, METERS_PER_MILE, SEARCH_RADIUS_MILES, CUISINE_TAGS } from "@culture-eats/core";
import type { CuisineTag, KitchenSearchResult } from "@culture-eats/core";

export interface CreateKitchenInput {
  name: string;
  cuisineTag: CuisineTag;
  description: string;
  address: string;
  lat: number;   // gercek geocoder Story 1.3 gorevi; simdilik istemciden/mocktan gelir
  lng: number;
}

@Injectable()
export class KitchensService {
  constructor(private prisma: PrismaService) {}

  async create(sellerId: string, input: CreateKitchenInput) {
    if (!CUISINE_TAGS.includes(input.cuisineTag)) throw new ForbiddenException("INVALID_CUISINE");
    const kitchen = await this.prisma.kitchen.create({
      data: {
        sellerId,
        name: input.name,
        cuisineTag: input.cuisineTag,
        description: input.description,
        addressEncrypted: encryptAddress(input.address), // NFR5
        photos: [],
      },
    });
    // PostGIS noktasi ham SQL ile yazilir (Prisma geography desteklemiyor)
    await this.prisma.$executeRaw`
      UPDATE "Kitchen" SET geo = ST_SetSRID(ST_MakePoint(${input.lng}, ${input.lat}), 4326)::geography
      WHERE id = ${kitchen.id}`;
    return this.publicProfile(kitchen.id);
  }

  /** FR5 + NFR2: 10 mil yaricapinda PostGIS aramasi, mesafe sirali. */
  async search(lat: number, lng: number, cuisine?: CuisineTag): Promise<KitchenSearchResult[]> {
    const radiusMeters = SEARCH_RADIUS_MILES * METERS_PER_MILE;
    const rows = await this.prisma.$queryRaw<any[]>`
      SELECT k.id, k.name, k."cuisineTag", k."ratingAvg", k."hygieneScoreTotal" AS hygiene,
             ST_Distance(k.geo, ST_SetSRID(ST_MakePoint(${lng}, ${lat}),4326)::geography) AS meters,
             COALESCE((
               SELECT SUM(mi."portionsRemaining") FROM "MenuItem" mi
               JOIN "MenuDay" md ON md.id = mi."menuDayId"
               WHERE md."kitchenId" = k.id AND md.status = 'published' AND md.date = CURRENT_DATE
             ), 0)::int AS portions_left
      FROM "Kitchen" k
      WHERE k."complianceAttestedAt" IS NOT NULL
        AND k.geo IS NOT NULL
        AND ST_DWithin(k.geo, ST_SetSRID(ST_MakePoint(${lng}, ${lat}),4326)::geography, ${radiusMeters})
        AND (${cuisine ?? null}::text IS NULL OR k."cuisineTag" = ${cuisine ?? null})
      ORDER BY meters ASC
      LIMIT 50`;
    return rows.map((r) => ({
      id: r.id,
      name: r.name,
      cuisineTag: r.cuisineTag,
      distanceMiles: Math.round((r.meters / METERS_PER_MILE) * 10) / 10,
      ratingAvg: r.ratingAvg,
      hygieneScore: r.hygiene,
      portionsLeftToday: r.portions_left,
    }));
  }

  /** Sokak adresi ASLA bu serializerdan cikmaz (FR10 / Story 1.3 AC3). */
  async publicProfile(id: string) {
    const k = await this.prisma.kitchen.findUnique({
      where: { id },
      select: {
        id: true, name: true, cuisineTag: true, description: true, photos: true,
        ratingAvg: true, ratingCount: true, hygieneScoreTotal: true, hygieneScoredAt: true,
        complianceAttestedAt: true,
      },
    });
    if (!k) throw new NotFoundException();
    return k;
  }

  /** Story 5.1 / get_menu: bir mutfagin tarihli yayinlanmis menusunu dondur. */
  async getPublishedMenu(kitchenId: string, date?: string) {
    const d = date ? new Date(date) : new Date();
    // date-only comparison: midnight UTC of requested day
    const dayStr = d.toISOString().slice(0, 10);
    const menuDay = await this.prisma.menuDay.findFirst({
      where: {
        kitchenId,
        status: "published",
        date: { gte: new Date(`${dayStr}T00:00:00.000Z`), lte: new Date(`${dayStr}T23:59:59.999Z`) },
      },
      include: { items: { include: { dish: true } } },
    });
    return menuDay ?? null;
  }

  async attest(sellerId: string, kitchenId: string, ip: string) {
    const k = await this.prisma.kitchen.findUnique({ where: { id: kitchenId } });
    if (!k || k.sellerId !== sellerId) throw new ForbiddenException();
    await this.prisma.kitchen.update({
      where: { id: kitchenId },
      data: { complianceAttestedAt: new Date(), attestationIp: ip },
    });
    await this.prisma.auditLog.create({
      data: { actor: sellerId, entity: `Kitchen:${kitchenId}`, action: "attest_compliance" },
    });
    return { attested: true };
  }
}
