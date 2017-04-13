package com.softwire.todos;

import com.atlassian.jira.rest.client.domain.Comment;
import com.atlassian.jira.rest.client.domain.Issue;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Code relating to the comment we add to the JIRA card listing the
 * open TODOs on that card.
 */
public class JiraCommenter {

    private static final String COMMENT_PREAMBLE =
            "Some TODOs in code comments reference this card.";

    private final String commentSearchJql;

    private final JiraClient jiraClient;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Config config;

    public JiraCommenter(Config config, JiraClient jiraClient) {
        this.config = config;
        this.jiraClient = jiraClient;

        commentSearchJql = "project = " + config.getJiraProjectKey()
                + " AND comment ~ \"" + COMMENT_PREAMBLE + "\"";

    }

    /**
     * Update all the JIRA card comments about TODOs.
     */
    public void updateJiraComments(Multimap<Issue, CodeTodo> todosByIssue) throws Exception {
        // 1. For all cards with current TODOs, update or create a comment
        for (Map.Entry<Issue, Collection<CodeTodo>> entry : todosByIssue.asMap().entrySet()) {
            Issue issue = entry.getKey();
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
                        existingComment,
                        Comment.valueOf(commentText));
            } else {
                log.debug("No change needed to comment on {}", issue.getKey());
            }
        }

        // 2. For any cards with a previous TODOs comment that no longer has
        // any todos, delete it:
        Set<Issue> issuesWithTodoComments =
                jiraClient.searchJqlWithFullIssues(commentSearchJql);

        for (Issue issue : issuesWithTodoComments) {

            // (Issue.equals works correctly here, see BasicIssue#equals)
            if (!todosByIssue.containsKey(issue)) {

                Comment todoComment = findTodoComment(issue);
                if (todoComment != null) {
                    jiraClient.deleteComment(issue, todoComment);
                }
            }
        }
    }

    private String createCommentText(Collection<CodeTodo> value) {
        checkArgument(!value.isEmpty());
        return String.format(
                "%s\n" +
                        "Please ensure they get resolved before closing.\n\n" +
                        "%s",
                COMMENT_PREAMBLE,
                value.stream()
                    .map(this::commentText)
                    .collect(Collectors.joining("\n")));
    }

    private String commentText(CodeTodo value) {
        String path = value.getFile().getPath().replace('\\', '/');

        return String.format(
                " * {{[%s:%s|%s/blob/master/%s#L%s]}}",
                path,
                value.getLine()
                        .replace("|", "\\|")
                        .replace("[", "\\[")
                        .replace("]", "\\]")
                        .replace("{", "\\{")
                        .replace("}", "\\}"),
                config.getGithubUrl(),
                path,
                value.getLineNumber());
    }

    private static Comment findTodoComment(Issue issue) {
        return Iterables.tryFind(
                issue.getComments(),
                (Comment c) -> c.getBody().startsWith(COMMENT_PREAMBLE))
                .orNull();
    }

    interface Config {
        String getJiraProjectKey();
        String getGithubUrl();
    }
}
