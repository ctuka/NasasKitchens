-- Story 6.2 (FR17): sellers poll buyers on candidate upcoming menu items.
-- PollVote enforces one vote per buyer per poll via a unique index.
CREATE TABLE "Poll" (
    "id" TEXT NOT NULL,
    "kitchenId" TEXT NOT NULL,
    "question" TEXT NOT NULL,
    "options" TEXT[],
    "closesAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Poll_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "PollVote" (
    "id" TEXT NOT NULL,
    "pollId" TEXT NOT NULL,
    "buyerId" TEXT NOT NULL,
    "optionIndex" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PollVote_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "Poll_kitchenId_createdAt_idx" ON "Poll"("kitchenId", "createdAt");
CREATE UNIQUE INDEX "PollVote_pollId_buyerId_key" ON "PollVote"("pollId", "buyerId");

ALTER TABLE "Poll" ADD CONSTRAINT "Poll_kitchenId_fkey"
    FOREIGN KEY ("kitchenId") REFERENCES "Kitchen"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "PollVote" ADD CONSTRAINT "PollVote_pollId_fkey"
    FOREIGN KEY ("pollId") REFERENCES "Poll"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE "PollVote" ADD CONSTRAINT "PollVote_buyerId_fkey"
    FOREIGN KEY ("buyerId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
