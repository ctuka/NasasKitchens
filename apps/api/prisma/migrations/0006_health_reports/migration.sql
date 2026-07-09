-- Story 7.1 (FR19): seller-uploaded health/permit documents (PDF or image), displayed
-- on the public kitchen profile with their upload date.
CREATE TABLE "HealthReport" (
    "id" TEXT NOT NULL,
    "kitchenId" TEXT NOT NULL,
    "fileUrl" TEXT NOT NULL,
    "filename" TEXT NOT NULL,
    "uploadedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "HealthReport_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "HealthReport_kitchenId_uploadedAt_idx" ON "HealthReport"("kitchenId", "uploadedAt");

ALTER TABLE "HealthReport" ADD CONSTRAINT "HealthReport_kitchenId_fkey"
    FOREIGN KEY ("kitchenId") REFERENCES "Kitchen"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
