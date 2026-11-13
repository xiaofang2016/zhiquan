package com.zhiquan.sport.api;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 文字响应在离开进程前改写域名。图片、声音和视频分片原样通过。
 */
@Component
public class Jx8RewriteFilter extends OncePerRequestFilter {

    private final DomainRewrites rewrites;

    public Jx8RewriteFilter(DomainRewrites rewrites) {
        this.rewrites = rewrites;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path == null || path.startsWith("/prod-api/domain-rewrites")
                || !(path.startsWith("/prod-api/") || path.startsWith("/api/sport/"))) {
            chain.doFilter(request, response);
            return;
        }
        Body responseBody = new Body(response, rewrites);
        chain.doFilter(request, responseBody);
        responseBody.finish();
    }

    private static final class Body extends HttpServletResponseWrapper {

        private final DomainRewrites rewrites;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private ServletOutputStream stream;
        private PrintWriter writer;
        private boolean decided;
        private boolean passthrough;
        private Long heldLength;

        private Body(HttpServletResponse response, DomainRewrites rewrites) {
            super(response);
            this.rewrites = rewrites;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (writer != null) {
                throw new IllegalStateException("getWriter() already called");
            }
            if (passthrough()) {
                return super.getOutputStream();
            }
            if (stream == null) {
                stream = new ServletOutputStream() {
                    @Override
                    public void write(int b) {
                        buffer.write(b);
                    }

                    @Override
                    public void write(byte[] bytes, int offset, int length) {
                        buffer.write(bytes, offset, length);
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener listener) {
                    }
                };
            }
            return stream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (stream != null) {
                throw new IllegalStateException("getOutputStream() already called");
            }
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8));
            }
            return writer;
        }

        @Override
        public void setContentLength(int length) {
            holdLength(length);
        }

        @Override
        public void setContentLengthLong(long length) {
            holdLength(length);
        }

        @Override
        public void setHeader(String name, String value) {
            if (lengthHeader(name)) {
                holdLength(value);
                return;
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (lengthHeader(name)) {
                holdLength(value);
                return;
            }
            super.addHeader(name, value);
        }

        @Override
        public void setIntHeader(String name, int value) {
            if (lengthHeader(name)) {
                holdLength(value);
                return;
            }
            super.setIntHeader(name, value);
        }

        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            if (passthrough) {
                super.flushBuffer();
            }
        }

        @Override
        public void reset() {
            buffer.reset();
            stream = null;
            writer = null;
            decided = false;
            passthrough = false;
            heldLength = null;
            super.reset();
        }

        private void finish() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            HttpServletResponse origin = (HttpServletResponse) getResponse();
            if (!decided) {
                if (heldLength != null && !origin.isCommitted()) {
                    origin.setContentLengthLong(heldLength);
                }
                return;
            }
            if (passthrough) {
                return;
            }
            byte[] raw = buffer.toByteArray();
            byte[] out = rewrite(getContentType(), raw) ? rewrites.apply(raw) : raw;
            if (!origin.isCommitted()) {
                origin.setContentLength(out.length);
            }
            origin.getOutputStream().write(out);
        }

        private boolean passthrough() throws IOException {
            if (!decided) {
                decided = true;
                String type = getContentType();
                passthrough = type != null && !type.isBlank() && !DomainRewrites.textual(type);
                if (passthrough && heldLength != null) {
                    ((HttpServletResponse) getResponse()).setContentLengthLong(heldLength);
                }
            }
            return passthrough;
        }

        private void holdLength(String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            try {
                holdLength(Long.parseLong(value.trim()));
            } catch (NumberFormatException ignored) {
                // 上游给的长度不是数字时，改写后再按实际正文计算。
            }
        }

        private void holdLength(long length) {
            heldLength = length;
            if (passthrough) {
                ((HttpServletResponse) getResponse()).setContentLengthLong(length);
            }
        }

        private static boolean lengthHeader(String name) {
            return name != null && name.equalsIgnoreCase("Content-Length");
        }

        private static boolean rewrite(String contentType, byte[] raw) {
            if (DomainRewrites.textual(contentType)) {
                return true;
            }
            if (contentType != null && !contentType.isBlank()) {
                return false;
            }
            int index = 0;
            while (index < raw.length && (raw[index] == ' ' || raw[index] == '\n' || raw[index] == '\r' || raw[index] == '\t')) {
                index++;
            }
            return index < raw.length && (raw[index] == '{' || raw[index] == '[');
        }
    }
}
