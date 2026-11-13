import assert from "node:assert/strict";
import test from "node:test";
import { rewrite } from "./jx8.mjs";

test("jx8 domain ignores case", () => {
  const text = rewrite({ content: "看 Jx8.Com" });
  assert.equal(JSON.parse(text).content, "看 JX8.CC");
});
