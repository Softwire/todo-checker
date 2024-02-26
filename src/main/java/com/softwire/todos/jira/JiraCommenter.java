package com.softwire.todos.jira;

import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.softwire.todos.CodeTodo;
import com.softwire.todos.JiraIssueReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Code relating to the comment we add to the JIRA card listing the
 * open TODOs on that card.
 */
public class JiraCommenter {

    private final String commentSearchJql;
    private final String commentPreamble;

    private final JiraClient jiraClient;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public JiraCommenter(Config config, JiraClient jiraClient) {
        this.jiraClient = jiraClient;

        StringBuilder commentPreambleBuilder = new StringBuilder();
        if (config.getJobName() != null) {
            commentPreambleBuilder.append(config.getJobName()).append(" - ");
        }
        commentPreambleBuilder.append("Some TODOs in code comments reference this card.");
        commentPreamble = commentPreambleBuilder.toString();

        String projects = config.getJiraProjects().stream()
                .map(jiraProject -> "project = " + jiraProject.getKey())
                .collect(Collectors.joining(" OR "));

        commentSearchJql = String.format("(%s) AND comment ~ \"%s\"", projects, commentPreamble);
    }

    /**
     * Update all the JIRA card comments about TODOs.
     */
    public void updateJiraComments(Multimap<JiraIssueReference, CodeTodo> todosByIssue) throws Exception {
        // 1. For all cards with current TODOs, update or create a comment
        for (Map.Entry<JiraIssueReference, Collection<CodeTodo>> entry : todosByIssue.asMap().entrySet()) {
            JiraIssueReference issueReference = entry.getKey();
            if (issueReference == null) {
                continue;
            }

            Issue issue = issueReference.getIssue();
            if (issue == null) {
                continue;
            }

            String commentText = createCommentText(entry.getValue());
            Comment existingComment = findTodoComment(issue);

            if (existingComment == null) {
                jiraClient.addComment(
                        issue,
                        Comment.valueOf(commentText));
            } else if (!existingComment.getBody().equals(commentText)) {
                jiraClient.updateComment(
                        issue,
                        new Comment(
                                existingComment.getSelf(),
                                commentText,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));
            } else {
                log.debug("No change needed to comment on {}", issue.getKey());
            }
        }

        // 2. For any cards with a previous TODOs comment that no longer has
        // any todos, delete it:
        Set<Issue> issuesWithTodoComments =
                jiraClient.searchIssuesWithComments(commentSearchJql);

        Set<Issue> referencedJiraIssues = todosByIssue.asMap().keySet().stream()
                .filter(Objects::nonNull)
                .map(JiraIssueReference::getIssue)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        issuesWithTodoComments.removeAll(referencedJiraIssues);

        for (Issue issue : issuesWithTodoComments) {
            Comment todoComment = findTodoComment(issue);
            if (todoComment != null) {
                jiraClient.deleteComment(issue, todoComment);
            }
        }
    }

    private String createCommentText(Collection<CodeTodo> value) {
        checkArgument(!value.isEmpty());
        return String.format(
                "%s\n" +
                        "Please ensure they get resolved before closing.\n\n" +
                        "%s",
                commentPreamble,
                value.stream()
                        .map(this::commentText)
                        .sorted()
                        .collect(Collectors.joining("\n")));
    }

    private String commentText(CodeTodo value) {
        String linkUrl = value.getSourceControlLinkUrl();

        String escapedCodeLine = value.getLine()
                .replace("|", "\\|")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replaceAll("([^-])-([A-Za-z])", "$1\\\\-$2")
                .replaceAll("--([A-Za-z])", "\\\\-\\\\-$1");

        // JIRA have been changing their comment format, and don't seem to have documented
        // the new format.
        // I have seen the following versions:
        // * Cloud JIRA on 2019-11-14, build number "100114" and one with build number "804002":
        //    - Comments seem to take Markdown syntax, there is no "Visual" v.s "Text" mode for comments
        //    - The UI editor does not allow linking inside `code` formatting
        //    - If we pass [link|url] inside {{code}} or `code`, it doesn't work
        //    - Although the UI seems to want markdown, the API still seems to want Atlassian wiki syntax
        // * On premises JIRA on 2019-11-14, build number "76011"
        //    - Comments box have "Visual" v.s "Text" mode, seem to use the old Atlassian wiki syntax
        //    - links inside {{code}} work fine
        //
        // See https://jira.atlassian.com/browse/JRACLOUD-69992 (now closed)
        if (jiraClient.getServerInfo().getBuildNumber() > 100000) {
            log.debug("This looks like a Cloud Jenkins, we cannot link inside code");
            return String.format(
                    "* [(view)|%s] {{%s:%s}}",
                    linkUrl,
                    value.getPosixPath(),
                    escapedCodeLine);
        } else {
            return String.format(
                    " * {{[%s:%s|%s]}}",
                    value.getPosixPath(),
                    escapedCodeLine,
                    linkUrl);
        }
    }

    private Comment findTodoComment(Issue issue) {
        return Iterables.tryFind(
                issue.getComments(),
                (Comment c) -> c.getBody().startsWith(commentPreamble))
                .orNull();
    }

    public interface Config {
        List<JiraProject> getJiraProjects();

        String getJobName();
    }
}
