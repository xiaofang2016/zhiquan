import assert from "node:assert/strict";
import { createCipheriv } from "node:crypto";
import test from "node:test";
import { decryptCover } from "./aesCfb.js";
import { decodeCover, encryptedCover } from "./coverImage.js";

test("decrypts an encrypted cover into the original bytes", () => {
  const encrypted = Buffer.from("90e685bdc39d8116d8e8210a0ec16645fbebee570e", "hex");
  assert.equal(Buffer.from(decryptCover(encrypted)).toString(), "0123456789abcdefHELLO");
});

test("a real encrypted jpeg cover decodes as jpeg", () => {
  const plain = Buffer.from([
    0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
    0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
  ]);
  const cipher = createCipheriv("aes-128-cfb", Buffer.from("cdemhsa16bbcjpy1"), Buffer.from("l81kvhvavn2990c5"));
  cipher.setAutoPadding(false);
  const encrypted = Buffer.concat([cipher.update(plain), cipher.final()]);
  assert.equal(Buffer.from(decodeCover(encrypted)).equals(plain), true);
  assert.equal(encryptedCover("https://imfile.dtthsp.com/video_rank/a/a_enc.jpg"), true);
  assert.equal(encryptedCover("https://imfile.dtthsp.com/message/1793591987547.jpg"), false);
});
