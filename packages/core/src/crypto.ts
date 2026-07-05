import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";

/** NFR5: ev adresleri uygulama katmaninda AES-256-GCM ile sifrelenir. */
const ALGO = "aes-256-gcm";

function key(): Buffer {
  const raw = process.env.ADDRESS_ENC_KEY ?? "";
  return Buffer.from(raw.padEnd(32, "0").slice(0, 32), "utf8");
}

export function encryptAddress(plain: string): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv(ALGO, key(), iv);
  const enc = Buffer.concat([cipher.update(plain, "utf8"), cipher.final()]);
  return [iv.toString("base64"), cipher.getAuthTag().toString("base64"), enc.toString("base64")].join(".");
}

export function decryptAddress(payload: string): string {
  const [iv, tag, data] = payload.split(".").map((p) => Buffer.from(p, "base64"));
  const decipher = createDecipheriv(ALGO, key(), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(data), decipher.final()]).toString("utf8");
}
