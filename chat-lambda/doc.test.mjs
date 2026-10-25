import assert from "node:assert/strict";
import test from "node:test";
import { empty, fromNim, isFresh, latest, merge, newerThan, olderThan, pack, present } from "./doc.mjs";

test("init keeps the latest hundred and older history stays in front", () => {
  const seeded = [];
  for (let i = 51; i <= 150; i += 1) seeded.push({ id: String(i), time: i, nick: "n", content: "m" + i });
  const first = merge(empty("4647552"), "room", seeded, {}).document;
  assert.equal(latest(first.messages, 100)[0].id, "51");
  assert.equal(latest(first.messages, 100).at(-1).id, "150");
  const second = merge(first, "room", [
    { id: "150", time: 150, nick: "n", content: "m150" },
    { id: "10", time: 10, nick: "n", content: "更早" },
    { id: "151", time: 151, nick: "n", content: "新" }
  ], {}).document;
  assert.equal(second.messages[0].id, "10");
  assert.equal(second.version.lastTime, 151);
  assert.equal(latest(second.messages, 100)[0].id, "52");
  assert.deepEqual(olderThan(second.messages, 52, 100).map((message) => message.id), ["10", "51"]);
  const withPicture = merge(second, "room", [
    { id: "151", time: 151, nick: "n", content: "新", images: [{ url: "https://cdn.example.com/a.png", w: 20, h: 10 }] }
  ], {}).document;
  assert.equal(withPicture.messages.at(-1).images[0].url, "https://cdn.example.com/a.png");
  assert.equal(withPicture.messages.at(-1).images[0].w, 20);
});

test("a recent sync can be served again without another pull", () => {
  const now = 1_000_000;
  const document = { ...empty("4498761"), syncedAt: now - 3000 };
  assert.equal(isFresh(document, now, 8000), true);
  assert.equal(isFresh({ ...document, syncedAt: now - 9000 }, now, 8000), false);
  assert.equal(isFresh(empty("4498761"), now, 8000), false);
});

test("image messages stay in the record without caption text", () => {
  const image = fromNim({
    type: "image",
    idClient: "pic1",
    time: 20,
    fromNick: "球迷",
    file: { url: "https://nim.example.com/a.jpg", w: 300, h: 200 }
  });
  const gift = fromNim({
    type: "text",
    idClient: "gift1",
    time: 21,
    fromNick: "球迷",
    text: "送了一个礼物",
    custom: JSON.stringify({ giftTrendsUrl: "https://cdn.example.com/gift.gif" })
  });
  assert.equal(image.content, "");
  assert.equal(image.images[0].url, "https://nim.example.com/a.jpg");
  assert.equal(image.images[0].h, 200);
  assert.equal(gift.images[0].url, "https://cdn.example.com/gift.gif");
  assert.equal(fromNim({ type: "notification", idClient: "n", time: 1, text: "进来了" }), null);
  const skinned = fromNim({
    type: "text",
    idClient: "c1",
    time: 3,
    fromNick: "甲",
    text: "你好",
    custom: JSON.stringify({ level: 50, bubbleUrl: "https://cdn.example.com/b.png", bobbleFrontColor: "rgb(130, 100, 169)" })
  });
  assert.equal(skinned.level, 50);
  assert.equal(skinned.bubble, "https://cdn.example.com/b.png");
  assert.equal(skinned.color, "rgb(130, 100, 169)");
  const shown = present(skinned);
  assert.ok(shown.badge.startsWith("https://") || shown.badge.startsWith("data:image/"));
  const packed = pack([skinned, { ...skinned, id: "c2", time: 4, content: "再来" }, { id: "c3", time: 5, nick: "乙", content: "没有等级" }]);
  assert.equal(Object.keys(packed.badges).length, 1);
  assert.ok(packed.badges[50].startsWith("https://") || packed.badges[50].startsWith("data:image/"));
  assert.equal(packed.messages[0].badge, undefined);
  assert.equal(packed.messages[1].badge, undefined);
  assert.equal(packed.messages[2].level, undefined);
  const times = [1, 2, 3, 4, 5].map((time) => ({ id: String(time), time, nick: "n", content: "m" }));
  assert.deepEqual(newerThan(times, 2).map((message) => message.time), [3, 4, 5]);
  assert.deepEqual(newerThan(times, 2, 2).map((message) => message.time), [3, 4]);
  assert.deepEqual(newerThan(times, 5).map((message) => message.time), []);
});
