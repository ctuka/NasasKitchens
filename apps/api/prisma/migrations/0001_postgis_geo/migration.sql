CREATE EXTENSION IF NOT EXISTS postgis;
ALTER TABLE "Kitchen" ADD COLUMN IF NOT EXISTS geo geography(Point, 4326);
CREATE INDEX IF NOT EXISTS kitchen_geo_gist ON "Kitchen" USING GIST (geo);
CREATE INDEX IF NOT EXISTS kitchen_cuisine_idx ON "Kitchen" ("cuisineTag");
