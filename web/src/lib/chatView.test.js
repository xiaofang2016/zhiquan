import assert from "node:assert/strict";
import test from "node:test";
import { imageSrc, poolDigits, present, previewText } from "./chatView.js";

test("red packet card uses the rain title and padded pool", () => {
  const view = present({
    type: "10",
    content: JSON.stringify({
      walletId: "677845",
      description: "每晚22:30开启红包雨🧧",
      dbAmountPool: "192958",
      status: "1",
      title: "元友红包"
    })
  });
  assert.equal(view.kind, "pack");
  assert.equal(view.title, "每晚22:30开启红包雨");
  assert.equal(view.status, "待领取");
  assert.equal(view.pool, "001929");
  assert.equal(poolDigits("118800"), "001188");
});

test("image message becomes a picture url", () => {
  const view = present({
    type: "13",
    content: JSON.stringify({
      text: "赢钱不给出款",
      files: [{ fileMd5: "1793518706579.jpg", w: 240, h: 353 }]
    })
  }, "https://cdn.example.com/");
  assert.equal(view.kind, "image");
  assert.equal(view.text, "赢钱不给出款");
  assert.equal(view.images[0], "https://cdn.example.com/message/1793518706579.jpg?imageView2/2/w/640/h/640&imageMogr2/format/webp");
  assert.equal(imageSrc({ fileMd5: "a.gif?imageMogr2/format/jpeg" }, "https://cdn.example.com"), "https://cdn.example.com/message/a.gif?imageMogr2/format/jpeg");
});

test("mention and preview do not dump raw json", () => {
  const mention = present({ type: "12", content: JSON.stringify({ origin_text: "@波胆串串中" }) });
  assert.equal(mention.kind, "text");
  assert.equal(mention.text, "@波胆串串中");
  assert.equal(previewText({ type: "13", content: JSON.stringify({ text: "", files: [{ fileMd5: "a.jpg" }] }) }), "[图片]");
});
