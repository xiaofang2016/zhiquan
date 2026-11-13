// 篮球直播右上角的白底域名。在 1280×720 画面上量过，大约 x 1019–1269、y 10–92，四周留一点。
const BASKET_DOMAIN = { x: 1008 / 1280, y: 4 / 720, w: 268 / 1280, h: 96 / 720 };

export function videoContentBox(boxWidth, boxHeight, videoWidth, videoHeight) {
  if (!(boxWidth > 0) || !(boxHeight > 0) || !(videoWidth > 0) || !(videoHeight > 0)) return null;
  const scale = Math.min(boxWidth / videoWidth, boxHeight / videoHeight);
  const width = videoWidth * scale;
  const height = videoHeight * scale;
  return {
    x: (boxWidth - width) / 2,
    y: (boxHeight - height) / 2,
    w: width,
    h: height
  };
}

export function watermarkPatch(sport, content) {
  if (Number(sport) !== 2 || !content) return null;
  return {
    left: content.x + content.w * BASKET_DOMAIN.x,
    top: content.y + content.h * BASKET_DOMAIN.y,
    width: content.w * BASKET_DOMAIN.w,
    height: content.h * BASKET_DOMAIN.h
  };
}
