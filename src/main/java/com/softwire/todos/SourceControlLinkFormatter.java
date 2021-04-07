package com.softwire.todos;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkArgument;

public abstract class SourceControlLinkFormatter {
    public abstract String build(String file, int line);

    public static class Github extends SourceControlLinkFormatter {
        private final String baseUrl;
        private final String gitBranchName;

        public Github(String baseUrl, String gitBranchName) {
            this.baseUrl = baseUrl;
            this.gitBranchName = gitBranchName;
        }

        public String build(String file, int line) {
            return String.format("%s/blob/%s/%s#L%s",
                    baseUrl,
                    urlEncode(gitBranchName),
                    urlEncodeExceptSlash(file),
                    line);
        }
    }

    public static class Gitblit extends SourceControlLinkFormatter {
        private final String baseUrl;
        private final String gitBranchName;

        public Gitblit(String baseUrl, String gitBranchName) {
            checkArgument(baseUrl.contains("?"), "baseUrl should have a query string part");
            this.baseUrl = baseUrl;
            this.gitBranchName = gitBranchName;
        }

        public String build(String file, int line) {
            return String.format("%s&f=%s&h=%s#L%s",
                    this.baseUrl,
                    urlEncode(file),
                    urlEncode(gitBranchName),
                    line);
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is always available.
            throw new RuntimeException("Unable to locate UTF-8 Charset", e);
        }
    }

    private static String urlEncodeExceptSlash(String s) {
        return urlEncode(s).replace("%2F", "/");
    }
}
