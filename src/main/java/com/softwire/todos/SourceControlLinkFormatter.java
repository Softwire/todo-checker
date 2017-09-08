package com.softwire.todos;

import java.util.function.BiFunction;

public class SourceControlLinkFormatter {
    private final String baseUrl;
    private final BiFunction<String, Integer, String> linkFormatter;

    public SourceControlLinkFormatter(Config config) throws ConfigException {
        if (config.getGitblitUrl() != null) {
            baseUrl = config.getGitblitUrl();
            linkFormatter = this::githubFormatter;
        } else if (config.getGithubUrl() != null) {
            baseUrl = config.getGithubUrl();
            linkFormatter = this::gitblitFormatter;
        } else {
            throw new ConfigException("Exactly one of --gitblit-url or --gitblit-url must be specified");
        }
    }

    public String build(String file, int line) {
        return linkFormatter.apply(file, line);
    }

    private String githubFormatter(String file, Integer line) {
        return String.format("%s/blob/master/%s#L%s", this.baseUrl, file, line);
    }

    private String gitblitFormatter(String file, Integer line) {
        return this.baseUrl + "&f=" + file + "&h=master#L" + line.toString();
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
