package com.softwire.todos;

import com.atlassian.jira.rest.client.api.domain.Issue;

import javax.annotation.Nullable;

public class JiraIssueReference {
    private final String id;
    private final Issue issue;

    public JiraIssueReference(String id, Issue issue) {
        this.id = id;
        this.issue = issue;
    }

    @Nullable
    public Issue getIssue() {
        return issue;
    }

    public String getId() {
        return id;
    }
}
