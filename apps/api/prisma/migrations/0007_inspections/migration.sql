-- Story 7.2 (FR20): admin-assigned inspection visits + submit-once hygiene scores.
-- The badge is denormalized to Kitchen.hygieneScoreTotal/hygieneScoredAt on submit.
CREATE TABLE "InspectionVisit" (
    "id" TEXT NOT NULL,
    "kitchenId" TEXT NOT NULL,
    "inspectorId" TEXT NOT NULL,
    "scheduledAt" TIMESTAMP(3) NOT NULL,
    "status" TEXT NOT NULL DEFAULT 'assigned',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "InspectionVisit_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "HygieneScore" (
    "id" TEXT NOT NULL,
    "visitId" TEXT NOT NULL,
    "total" INTEGER NOT NULL,
    "subScores" JSONB NOT NULL,
    "photos" TEXT[],
    "submittedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "HygieneScore_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "InspectionVisit_inspectorId_scheduledAt_idx"
    ON "InspectionVisit"("inspectorId", "scheduledAt");
CREATE UNIQUE INDEX "HygieneScore_visitId_key" ON "HygieneScore"("visitId");

ALTER TABLE "InspectionVisit" ADD CONSTRAINT "InspectionVisit_kitchenId_fkey"
    FOREIGN KEY ("kitchenId") REFERENCES "Kitchen"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "InspectionVisit" ADD CONSTRAINT "InspectionVisit_inspectorId_fkey"
    FOREIGN KEY ("inspectorId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "HygieneScore" ADD CONSTRAINT "HygieneScore_visitId_fkey"
    FOREIGN KEY ("visitId") REFERENCES "InspectionVisit"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
