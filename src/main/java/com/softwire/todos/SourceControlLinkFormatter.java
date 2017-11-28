package com.softwire.todos;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;

public class SourceControlLinkFormatter {
    private final String baseUrl;
    private final BiFunction<String, Integer, String> linkFormatter;

    public SourceControlLinkFormatter(Config config) throws ConfigException {
        if (config.getGitblitUrl() != null) {
            baseUrl = config.getGitblitUrl();
            linkFormatter = this::gitblitFormatter;
        } else if (config.getGithubUrl() != null) {
            baseUrl = config.getGithubUrl();
            linkFormatter = this::githubFormatter;
        } else {
            throw new ConfigException("Exactly one of --github-url or --gitblit-url must be specified");
        }
    }

    public String build(String file, int line) {
        return linkFormatter.apply(file, line);
    }

    private String githubFormatter(String file, Integer line) {
        return String.format("%s/blob/master/%s#L%s", this.baseUrl, file, line);
    }

    private String gitblitFormatter(String file, Integer line) {
        try {
            return String.format("%s&f=%s&h=master#L%s",
                    this.baseUrl,
                    URLEncoder.encode(file, StandardCharsets.UTF_8.toString()),
                    line.toString());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is always available.
            throw new RuntimeException("Unable to locate UTF-8 Charset", e);
        }
    }

    public interface Config {
        String getGithubUrl();
        String getGitblitUrl();
    }

    public class ConfigException extends Exception {
        ConfigException(String message) {
            super(message);
        }
    }
}
