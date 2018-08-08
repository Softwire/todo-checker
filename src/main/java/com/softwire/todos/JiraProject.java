package com.softwire.todos;

import java.util.regex.Pattern;

public class JiraProject {
    private final String key;
    private final String regex;

    JiraProject(String key, String regex) {
        this.key = key;
        this.regex = regex;
    }

    public String getKey() {
        return key;
    }

    public String getRegex() {
        return regex;
    }

    Pattern getIssueIdPattern() {
        return Pattern.compile(
            "(" + getRegex() + ")[-_:](?<id>[0-9]+)",
            Pattern.CASE_INSENSITIVE);
    }
}
