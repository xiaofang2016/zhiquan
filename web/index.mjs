import { readFileSync } from "node:fs";

const html = readFileSync(new URL("./index.html", import.meta.url));

export const handler = async () => ({
  statusCode: 200,
  headers: {
    "content-type": "text/html; charset=utf-8",
    "cache-control": "no-cache",
    "x-backend": "lambda"
  },
  body: html.toString("utf8")
});
