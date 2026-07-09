-- Story 6.3 (FR18): a buyer submits a dish/cuisine request to a kitchen; the seller
-- accepts (notifying the requester) or declines. status: open | accepted | declined.
CREATE TABLE "DishRequest" (
    "id" TEXT NOT NULL,
    "kitchenId" TEXT NOT NULL,
    "buyerId" TEXT NOT NULL,
    "text" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'open',
    "sellerNote" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "DishRequest_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "DishRequest_kitchenId_createdAt_idx" ON "DishRequest"("kitchenId", "createdAt");
CREATE INDEX "DishRequest_buyerId_createdAt_idx" ON "DishRequest"("buyerId", "createdAt");

ALTER TABLE "DishRequest" ADD CONSTRAINT "DishRequest_kitchenId_fkey"
    FOREIGN KEY ("kitchenId") REFERENCES "Kitchen"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "DishRequest" ADD CONSTRAINT "DishRequest_buyerId_fkey"
    FOREIGN KEY ("buyerId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
