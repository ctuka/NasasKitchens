-- Story 4.4 follow-up (FR22): per-user opt-out for external notification channels.
-- disabled holds "category:channel" keys (e.g. "community:email"); an absent row means
-- everything is on. The in-app inbox is never gated by this.
CREATE TABLE "NotificationPreference" (
    "userId" TEXT NOT NULL,
    "disabled" TEXT[],
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NotificationPreference_pkey" PRIMARY KEY ("userId")
);

ALTER TABLE "NotificationPreference" ADD CONSTRAINT "NotificationPreference_userId_fkey"
    FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
