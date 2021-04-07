package com.softwire.todos;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;

public abstract class SourceControlLinkFormatter {
    public abstract String build(String file, int line);

    public static class Github extends SourceControlLinkFormatter {
        private final String baseUrl;

        public Github(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String build(String file, int line) {
            return String.format("%s/blob/main/%s#L%s", this.baseUrl, file, line);
        }
    }

    public static class Gitblit extends SourceControlLinkFormatter {
        private final String baseUrl;

        public Gitblit(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String build(String file, int line) {
            try {
                return String.format("%s&f=%s&h=master#L%s",
                    this.baseUrl,
                    URLEncoder.encode(file, StandardCharsets.UTF_8.toString()),
                    line);
            } catch (UnsupportedEncodingException e) {
                // UTF-8 is always available.
                throw new RuntimeException("Unable to locate UTF-8 Charset", e);
            }
        }
    }
}
