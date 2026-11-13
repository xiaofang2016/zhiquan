import assert from "node:assert/strict";
import test from "node:test";
import { rewriteJx8 } from "./jx8.js";

test("default pair ignores case and keeps the whole name", () => {
  const body = rewriteJx8({ name: "JX8.com 对 jx8.COM，www.jx8.com 不动，qiumishuo.com 换成 qiumishuo.cc" });
  assert.equal(body.name, "JX8.CC 对 JX8.CC，www.jx8.com 不动，qiumishuo.cc 换成 qiumishuo.cc");
});

test("each name maps to its own target", () => {
  const body = rewriteJx8({ name: "www.jx8.com 和 https://JX8.com/a" }, [
    { from: "www.jx8.com", to: "www.JX8.CC" },
    { from: "JX8.com", to: "JX8.CC" }
  ]);
  assert.equal(body.name, "www.JX8.CC 和 https://JX8.CC/a");
});
