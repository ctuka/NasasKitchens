-- Story 3.4 / FR21 completion: declined or cancelled orders whose payment was already
-- captured are refunded automatically through the PaymentProvider; these columns record
-- the provider refund id and when it was issued (null = nothing was ever captured).
ALTER TABLE "Order" ADD COLUMN "refundId" TEXT;
ALTER TABLE "Order" ADD COLUMN "refundedAt" TIMESTAMP(3);
