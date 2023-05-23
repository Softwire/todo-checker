package com.softwire.todos;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.apache.commons.lang3.builder.HashCodeBuilder;

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

    @Override
    public int hashCode() {
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(id);
        hashCodeBuilder.append(issue);
        return hashCodeBuilder.toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JiraIssueReference))
            return false;

        JiraIssueReference that = (JiraIssueReference) obj;
        return that.id.equals(this.id) && that.issue.equals(this.issue);
    }
}
