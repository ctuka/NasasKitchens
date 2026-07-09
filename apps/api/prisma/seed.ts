/**
 * Demo seed: 1 buyer + 3 sellers with attested kitchens near downtown SF,
 * each with dishes and a published menu for today with live portions.
 *
 * Run: pnpm seed   (from apps/api)
 * Login: buyer@demo.com / demo1234
 */
import { PrismaClient } from "@prisma/client";
import * as argon2 from "argon2";
import { encryptAddress } from "@culture-eats/core";

const prisma = new PrismaClient();

// Test location: Union Square, San Francisco
const CENTER = { lat: 37.788, lng: -122.4075 };

// Demo food photography (Unsplash hotlinks) until seller photo upload (S3) ships.
const IMG = (id: string) => `https://images.unsplash.com/photo-${id}?w=800&q=60`;

const KITCHENS = [
  {
    seller: { email: "ayse@demo.com" },
    name: "Ayse's Anatolian Kitchen",
    cuisineTag: "turkish",
    description: "Home-style Turkish classics: manti, dolma, fresh pide.",
    address: "350 Post St, San Francisco, CA",
    photos: [IMG("1529006557810-274b9b2fc783")],
    lat: 37.7885, lng: -122.4078, // ~0.1 mi
    dishes: [
      { name: "Manti (Turkish dumplings)", description: "Hand-folded beef dumplings with garlic yogurt", priceCents: 1450, dietaryTags: [], portions: 8, photo: IMG("1534422298391-e4f8c172dddb") },
      { name: "Vegetarian Dolma", description: "Grape leaves stuffed with rice, herbs, pine nuts", priceCents: 1150, dietaryTags: ["vegetarian", "vegan"], portions: 12, photo: IMG("1512621776951-a57141f2eefd") },
      { name: "Lahmacun", description: "Thin crispy flatbread with spiced minced lamb", priceCents: 950, dietaryTags: [], portions: 10, photo: IMG("1565299624946-b28f40a0ae38") },
    ],
  },
  {
    seller: { email: "mei@demo.com" },
    name: "Mei's Sichuan Home Cooking",
    cuisineTag: "chinese",
    description: "Bold Sichuan flavors from a family wok: mapo tofu, dan dan noodles.",
    address: "728 Pacific Ave, San Francisco, CA",
    photos: [IMG("1455619452474-d2be8b1e70cd")],
    lat: 37.7967, lng: -122.4097, // ~0.6 mi
    dishes: [
      { name: "Mapo Tofu", description: "Silky tofu in numbing-spicy chili bean sauce", priceCents: 1250, dietaryTags: ["vegetarian"], portions: 10, photo: IMG("1504674900247-0877df9cc836") },
      { name: "Dan Dan Noodles", description: "Hand-pulled noodles, sesame-chili sauce, minced pork", priceCents: 1350, dietaryTags: [], portions: 8, photo: IMG("1476224203421-9ac39bcb3327") },
      { name: "Cucumber Salad", description: "Smashed cucumber, garlic, black vinegar", priceCents: 650, dietaryTags: ["vegan", "gluten-free"], portions: 15, photo: IMG("1546069901-ba9599a7e63c") },
    ],
  },
  {
    seller: { email: "rosa@demo.com" },
    name: "Rosa's Cocina Oaxaquena",
    cuisineTag: "mexican",
    description: "Oaxacan mole, handmade tortillas, tamales like abuela made.",
    address: "2889 Mission St, San Francisco, CA",
    photos: [IMG("1599974579688-8dbdd335c77f")],
    lat: 37.7517, lng: -122.4183, // ~2.6 mi
    dishes: [
      { name: "Mole Negro con Pollo", description: "Chicken in 28-ingredient black mole, rice, tortillas", priceCents: 1650, dietaryTags: [], portions: 6, photo: IMG("1565299585323-38d6b0865b47") },
      { name: "Tamales de Rajas", description: "Poblano & cheese tamales in banana leaf (2 pc)", priceCents: 1050, dietaryTags: ["vegetarian"], portions: 12, photo: IMG("1551504734-5ee1c4a1479b") },
      { name: "Tlayuda", description: "Crispy tortilla, beans, quesillo, avocado", priceCents: 1250, dietaryTags: ["vegetarian"], portions: 9, photo: IMG("1555939594-58d7cb561ad1") },
    ],
  },
];

async function main() {
  const passwordHash = await argon2.hash("demo1234");

  // Buyer + platform-invited roles (Story 7.2: inspectors/admins have no open signup)
  for (const u of [
    { email: "buyer@demo.com", role: "buyer" },
    { email: "inspector@demo.com", role: "inspector" },
    { email: "admin@demo.com", role: "admin" },
  ] as const) {
    await prisma.user.upsert({
      where: { email: u.email },
      update: {},
      create: { email: u.email, passwordHash, role: u.role },
    });
    console.log(`${u.email} / demo1234`);
  }

  const today = new Date(new Date().toISOString().slice(0, 10)); // midnight UTC today

  for (const k of KITCHENS) {
    const seller = await prisma.user.upsert({
      where: { email: k.seller.email },
      update: {},
      create: { email: k.seller.email, passwordHash, role: "seller" },
    });

    let kitchen = await prisma.kitchen.findUnique({ where: { sellerId: seller.id } });
    if (!kitchen) {
      kitchen = await prisma.kitchen.create({
        data: {
          sellerId: seller.id,
          name: k.name,
          cuisineTag: k.cuisineTag,
          description: k.description,
          addressEncrypted: encryptAddress(k.address),
          photos: k.photos,
          complianceAttestedAt: new Date(), // attested so it appears in search
        },
      });
      await prisma.$executeRaw`
        UPDATE "Kitchen" SET geo = ST_SetSRID(ST_MakePoint(${k.lng}, ${k.lat}), 4326)::geography
        WHERE id = ${kitchen.id}`;
    }

    // Published menu for today (idempotent via @@unique([kitchenId, date]))
    const existing = await prisma.menuDay.findUnique({
      where: { kitchenId_date: { kitchenId: kitchen.id, date: today } },
    });
    if (!existing) {
      const menuDay = await prisma.menuDay.create({
        data: {
          kitchenId: kitchen.id,
          date: today,
          status: "published",
          readyWindows: [{ start: "17:00", end: "20:00", slotMinutes: 30 }],
        },
      });
      for (const d of k.dishes) {
        const dish = await prisma.dish.create({
          data: {
            kitchenId: kitchen.id,
            name: d.name,
            description: d.description,
            photo: d.photo,
            priceCents: d.priceCents,
            dietaryTags: d.dietaryTags,
          },
        });
        await prisma.menuItem.create({
          data: { menuDayId: menuDay.id, dishId: dish.id, portionsTotal: d.portions, portionsRemaining: d.portions },
        });
      }
    }
    console.log(`${k.name} — ${k.dishes.length} dishes, menu published for today`);
  }

  console.log(`\nSearch from lat=${CENTER.lat} lng=${CENTER.lng} (Union Square, SF)`);
}

main()
  .catch((e) => { console.error(e); process.exit(1); })
  .finally(() => prisma.$disconnect());
