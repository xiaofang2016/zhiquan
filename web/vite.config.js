import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

function inlinePage() {
  return {
    name: "inline-page",
    apply: "build",
    enforce: "post",
    generateBundle(_, bundle) {
      const htmlFile = bundle["index.html"];
      if (!htmlFile || htmlFile.type !== "asset") return;
      let html = htmlFile.source.toString();
      const remove = [];
      for (const [name, file] of Object.entries(bundle)) {
        const quoted = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        if (file.type === "chunk" && name.endsWith(".js")) {
          const code = file.code.replace(/<\/script/gi, "<\\/script");
          const next = html.replace(
            new RegExp(`<script\\b[^>]*\\bsrc=["'][^"']*${quoted}["'][^>]*>\\s*</script>`),
            () => `<script type="module">${code}</script>`
          );
          if (next === html) this.error("脚本没有写进页面");
          html = next;
          remove.push(name);
        }
        if (file.type === "asset" && name.endsWith(".css")) {
          const css = file.source.toString().replace(/<\/style/gi, "<\\/style");
          const next = html.replace(
            new RegExp(`<link\\b[^>]*\\bhref=["'][^"']*${quoted}["'][^>]*>`),
            () => `<style>${css}</style>`
          );
          if (next === html) this.error("样式没有写进页面");
          html = next;
          remove.push(name);
        }
      }
      if (/<script\b[^>]*\bsrc=/.test(html) || /<link\b[^>]*\brel=["']stylesheet["']/.test(html)) {
        this.error("页面还引用了外部脚本或样式");
      }
      for (const name of remove) delete bundle[name];
      htmlFile.source = html;
    }
  };
}

export default defineConfig({
  plugins: [vue(), inlinePage()],
  publicDir: "public",
  build: {
    outDir: "dist",
    emptyOutDir: true
  }
});
