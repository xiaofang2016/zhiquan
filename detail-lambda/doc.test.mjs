import assert from "node:assert/strict";
import test from "node:test";
import { empty, publicTab, putSection, readSection, sectionOf } from "./doc.mjs";

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

test("each public path selects one section", () => {
  assert.equal(sectionOf("/live", ""), "live");
  assert.equal(sectionOf("/index", ""), "odds");
  assert.equal(sectionOf("/data", ""), "data");
  assert.equal(sectionOf("/tip", ""), "tip");
  assert.equal(sectionOf("/detail", "live"), "live");
  assert.equal(sectionOf("/index.html", ""), "");
  assert.equal(publicTab("odds"), "index");
});
