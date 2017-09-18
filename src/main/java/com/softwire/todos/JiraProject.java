package com.softwire.todos;

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
}
