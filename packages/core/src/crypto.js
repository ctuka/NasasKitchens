"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.encryptAddress = encryptAddress;
exports.decryptAddress = decryptAddress;
const node_crypto_1 = require("node:crypto");
/** NFR5: ev adresleri uygulama katmaninda AES-256-GCM ile sifrelenir. */
const ALGO = "aes-256-gcm";
function key() {
    const raw = process.env.ADDRESS_ENC_KEY ?? "";
    return Buffer.from(raw.padEnd(32, "0").slice(0, 32), "utf8");
}
function encryptAddress(plain) {
    const iv = (0, node_crypto_1.randomBytes)(12);
    const cipher = (0, node_crypto_1.createCipheriv)(ALGO, key(), iv);
    const enc = Buffer.concat([cipher.update(plain, "utf8"), cipher.final()]);
    return [iv.toString("base64"), cipher.getAuthTag().toString("base64"), enc.toString("base64")].join(".");
}
function decryptAddress(payload) {
    const [iv, tag, data] = payload.split(".").map((p) => Buffer.from(p, "base64"));
    const decipher = (0, node_crypto_1.createDecipheriv)(ALGO, key(), iv);
    decipher.setAuthTag(tag);
    return Buffer.concat([decipher.update(data), decipher.final()]).toString("utf8");
}
//# sourceMappingURL=crypto.js.map