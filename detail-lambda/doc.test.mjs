import assert from "node:assert/strict";
import test from "node:test";
import { empty, putSection, readSection } from "./doc.mjs";

test("live odds refresh and the other tabs stay stored", () => {
  const now = 1_000_000;
  let document = putSection(empty("4647552"), "live", { tlive: [] }, now);
  document = putSection(document, "data", { history: { vs: [] } }, now);
  assert.equal(readSection(document, "live", now + 14000).stale, false);
  assert.equal(readSection(document, "live", now + 15000).stale, true);
  assert.equal(readSection(document, "data", now + 86400000).stale, false);
  assert.equal(readSection(document, "tip", now).stale, true);
  assert.equal(readSection(document, "odds", now).body, null);
});
