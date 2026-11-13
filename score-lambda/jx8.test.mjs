import assert from "node:assert/strict";
import test from "node:test";
import { applyText, compile, rewrite } from "./jx8.mjs";

test("jx8 domain ignores case", () => {
  const text = rewrite({ name: "去 JX8.com 和 jx8.COM，还有 qiumishuo.com" });
  assert.equal(JSON.parse(text).name, "去 JX8.CC 和 JX8.CC，还有 qiumishuo.cc");
});

test("each name maps to its own target", () => {
  const rules = compile("www.jx8.com=www.JX8.CC\nJX8.com=JX8.CC\nJX8.com=JX8.NET\n");
  assert.deepEqual(rules.map((pair) => pair.to), ["www.JX8.CC", "JX8.NET"]);
  assert.equal(applyText("看 www.jx8.com 和 jx8.com.cn", rules), "看 www.JX8.CC 和 jx8.com.cn");
});
