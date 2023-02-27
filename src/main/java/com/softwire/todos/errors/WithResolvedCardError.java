package com.softwire.todos.errors;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.softwire.todos.CodeTodo;

import java.util.Collection;

public class WithResolvedCardError extends TodoCheckerError {
    private final Issue issue;
    private final String resolutionName;

    public WithResolvedCardError(Collection<CodeTodo> codeTodos, Issue issue, String resolutionName) {
        super(codeTodos);
        this.issue = issue;
        this.resolutionName = resolutionName;
    }

    public Issue getIssue() {
        return issue;
    }

    public String getResolutionName() {
        return resolutionName;
    }
}
