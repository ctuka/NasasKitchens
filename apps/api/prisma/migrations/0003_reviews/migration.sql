-- Story 6.1 (FR16): buyers rate a kitchen 1-5 only after a completed order.
-- One review per order; Kitchen.ratingAvg/ratingCount recomputed on insert.
CREATE TABLE "Review" (
    "id" TEXT NOT NULL,
    "orderId" TEXT NOT NULL,
    "kitchenId" TEXT NOT NULL,
    "buyerId" TEXT NOT NULL,
    "rating" INTEGER NOT NULL,
    "comment" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Review_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "Review_orderId_key" ON "Review"("orderId");

ALTER TABLE "Review" ADD CONSTRAINT "Review_orderId_fkey"
    FOREIGN KEY ("orderId") REFERENCES "Order"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "Review" ADD CONSTRAINT "Review_kitchenId_fkey"
    FOREIGN KEY ("kitchenId") REFERENCES "Kitchen"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "Review" ADD CONSTRAINT "Review_buyerId_fkey"
    FOREIGN KEY ("buyerId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

CREATE INDEX "Review_kitchenId_createdAt_idx" ON "Review"("kitchenId", "createdAt");
