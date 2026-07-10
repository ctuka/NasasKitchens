-- Buyer drop-off address for delivery orders, encrypted app-side (NFR5)
ALTER TABLE "Order" ADD COLUMN "deliveryAddressEncrypted" TEXT;
