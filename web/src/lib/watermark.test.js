import assert from "node:assert/strict";
import test from "node:test";
import { videoContentBox, watermarkPatch } from "./watermark.js";

test("basketball patch covers the top-right domain on the picture", () => {
  const content = videoContentBox(1280, 720, 1280, 720);
  const patch = watermarkPatch(2, content);
  assert.ok(patch.left <= 1019 && patch.left + patch.width >= 1269);
  assert.ok(patch.top <= 10 && patch.top + patch.height >= 92);
  assert.ok(patch.top + patch.height < 180);
});

test("letterbox keeps the patch on the picture, not the bars", () => {
  const content = videoContentBox(1280, 1000, 1280, 720);
  const patch = watermarkPatch(2, content);
  assert.equal(content.y, 140);
  assert.ok(patch.top >= 140);
});

test("football and animation have no patch", () => {
  const content = videoContentBox(1920, 1080, 1920, 1080);
  assert.equal(watermarkPatch(1, content), null);
  assert.equal(watermarkPatch(3, content), null);
  assert.equal(videoContentBox(0, 720, 1280, 720), null);
});
