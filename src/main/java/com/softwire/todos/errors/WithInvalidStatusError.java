package com.softwire.todos.errors;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.softwire.todos.CodeTodo;

import java.util.Collection;

public class WithInvalidStatusError extends TodoCheckerError {
    private final Issue issue;
    private final String statusName;

    public WithInvalidStatusError(Collection<CodeTodo> value, Issue issue, String statusName) {
        super(value);

        this.issue = issue;
        this.statusName = statusName;
    }

    public Issue getIssue() {
        return issue;
    }

    public String getStatusName() {
        return statusName;
    }
}
