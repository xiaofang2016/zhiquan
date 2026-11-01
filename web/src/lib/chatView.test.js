import assert from "node:assert/strict";
import test from "node:test";
import { groupAvatar, imageSrc, poolDigits, present, previewText, textParts } from "./chatView.js";

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
  assert.equal(view.count, "");
  const named = present({
    type: "10",
    nick: "UED-红包达人",
    content: JSON.stringify({ walletId: "1", description: "西甲赞助商", dbAmountPool: "123500", status: "1", num: "200", remainNum: "171" })
  });
  assert.equal(named.from, "UED-红包达人");
  assert.equal(named.count, "剩余 171/200");
  assert.equal(named.pool, "001235");
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

test("revoke json becomes a system line and faces split out of text", () => {
  const view = present({
    type: "91",
    nick: "莉莉",
    content: JSON.stringify({
      uid: "400275",
      nickName: "吉祥/UED高级专属 莉莉",
      beUid: "5647573",
      beNickName: "安亿十",
      fpForMessage: "499799df-2a0a-4525-a615-3aebc8937d03",
      originalContent: null
    })
  });
  assert.equal(view.kind, "revoke");
  assert.equal(view.text, "吉祥/UED高级专属 莉莉撤回了\"安亿十\"的一条消息");
  const own = present({ type: "0", content: JSON.stringify({ uid: "1", nickName: "佩奇", fpForMessage: "abc" }) });
  assert.equal(own.text, "\"佩奇\" 撤回了一条消息");
  const parts = textParts("你好[/发呆]呀[/不是表情]");
  assert.deepEqual(parts.map((part) => part.kind === "face" ? part.src : part.text), ["你好", "/face/1f633.png", "呀[/不是表情]"]);
  assert.equal(previewText({ type: "0", content: "[/发呆]" }), "[/发呆]");
});

test("group avatar uses the head path", () => {
  assert.equal(
    groupAvatar("01c47016f1acca765c4ef6312ebcf104", "https://imfile.dtthsp.com/"),
    "https://imfile.dtthsp.com/head/01c47016f1acca765c4ef6312ebcf104?imageView2/1/w/256/h/256&imgType=group&imageMogr2/format/webp"
  );
  assert.equal(groupAvatar("https://cdn.example.com/a.jpg", ""), "https://cdn.example.com/a.jpg");
  assert.equal(groupAvatar("", "https://imfile.dtthsp.com"), "");
});

test("mention and preview do not dump raw json", () => {
  const mention = present({ type: "12", content: JSON.stringify({ origin_text: "@波胆串串中" }) });
  assert.equal(mention.kind, "text");
  assert.equal(mention.text, "@波胆串串中");
  assert.equal(previewText({ type: "13", content: JSON.stringify({ text: "", files: [{ fileMd5: "a.jpg" }] }) }), "[图片]");
});
