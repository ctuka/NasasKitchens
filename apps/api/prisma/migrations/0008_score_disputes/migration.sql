-- Story 7.3: seller-raised hygiene-score disputes, resolved by an admin who either
-- dismisses (score stands) or annuls the badge (kitchen returns to "Not yet inspected").
CREATE TABLE "ScoreDispute" (
    "id" TEXT NOT NULL,
    "kitchenId" TEXT NOT NULL,
    "openedById" TEXT NOT NULL,
    "reason" TEXT NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'open',
    "adminNote" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "resolvedAt" TIMESTAMP(3),

    CONSTRAINT "ScoreDispute_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "ScoreDispute_status_createdAt_idx" ON "ScoreDispute"("status", "createdAt");

ALTER TABLE "ScoreDispute" ADD CONSTRAINT "ScoreDispute_kitchenId_fkey"
    FOREIGN KEY ("kitchenId") REFERENCES "Kitchen"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "ScoreDispute" ADD CONSTRAINT "ScoreDispute_openedById_fkey"
    FOREIGN KEY ("openedById") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
