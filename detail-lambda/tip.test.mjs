import assert from "node:assert/strict";
import test from "node:test";
import { presentTip } from "./upstream.mjs";

test("tip drops the corner market", () => {
  const body = presentTip({ forecast: { rq: { pankou: [] }, jq: { pankou: [] } }, intel: null });
  assert.equal(body.forecast.jq, undefined);
  assert.ok(body.forecast.rq);
  assert.equal(presentTip({ forecast: { sf: {} } }).forecast.sf.pankou, undefined);
});
