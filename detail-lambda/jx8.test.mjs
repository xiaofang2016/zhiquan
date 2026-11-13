import assert from "node:assert/strict";
import test from "node:test";
import { rewrite } from "./jx8.mjs";

test("jx8 domain ignores case", () => {
  const text = rewrite({ name: "去 JX8.com 和 jx8.COM" });
  assert.equal(JSON.parse(text).name, "去 JX8.CC 和 JX8.CC");
});
