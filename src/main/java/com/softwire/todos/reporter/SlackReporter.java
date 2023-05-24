package com.softwire.todos.reporter;

import com.softwire.todos.CodeTodo;
import com.softwire.todos.errors.TodoCheckerErrors;
import com.softwire.todos.errors.WithInvalidStatusError;
import com.softwire.todos.errors.WithResolvedCardError;
import com.softwire.todos.errors.WithoutCardError;
import com.softwire.todos.jira.JiraClient;
import com.softwire.todos.slack.SlackClient;
import com.softwire.todos.slack.SlackClientException;

import java.util.Collection;

public class SlackReporter implements Reporter {
    public static final int MAX_MESSAGES_POSTED = 6;
    private final SlackClient slackClient;
    private final JiraClient jiraClient;

    public SlackReporter(SlackClient slackClient, JiraClient jiraClient) {
        this.slackClient = slackClient;
        this.jiraClient = jiraClient;
    }

    /**
     * Generate a Slack message per JIRA card, so that it's easier for people to reply on each thread with cards which
     * they want to claim.
     */
    public void report(TodoCheckerErrors errors) throws Exception {
        if (errors.isSuccess()) {
            slackClient.postMessage(
                    ":white_check_mark: TODO Checker successful: " +
                    "no TODOs without JIRA cards or TODOs on closed cards."
            );
            return ;
        }

        int messagesPosted = 0;
        for (WithResolvedCardError error : errors.getWithResolvedCardErrors()) {
            if (tooManyMessages(messagesPosted)) {
                return;
            }
            messagesPosted += 1;

            reportError(error.getCodeTodos(), String.format(
                    ":x: JIRA card <%s|%s> with resolution '%s' has outstanding TODOs:\n",
                    jiraClient.getViewUrl(error.getIssue()),
                    error.getIssue().getKey(),
                    error.getResolutionName()
            ));
        }

        for (WithInvalidStatusError error : errors.getWithInvalidStatusErrors()) {
            if (tooManyMessages(messagesPosted)) {
                return;
            }
            messagesPosted += 1;

            reportError(error.getCodeTodos(), String.format(
                    ":x: JIRA card <%s|%s> with status '%s' has outstanding TODOs:\n",
                    jiraClient.getViewUrl(error.getIssue()),
                    error.getIssue().getKey(),
                    error.getStatusName()
            ));
        }

        for (WithoutCardError error: errors.getWithoutCardErrors()) {
            if (tooManyMessages(messagesPosted)) {
                return;
            }
            messagesPosted += 1;

            reportError(error.getCodeTodos(), ":x: TODOs without a JIRA card found:\n");
        }
    }

    private boolean tooManyMessages(int messagesPosted) throws SlackClientException {
        if (messagesPosted >= MAX_MESSAGES_POSTED) {
            slackClient.postMessage(
                    ":x: Further JIRA cards found with invalid TODOs found, but will not be posted to Slack to " +
                            "avoid an excessive number of messages");
            return true;
        }
        return false;
    }

    private void reportError(Collection<CodeTodo> codeTodos, String headline) throws Exception {
        StringBuilder report = new StringBuilder();
        report.append(headline);

        for (CodeTodo codeTodo : codeTodos) {
            String filename = codeTodo.getFile().getPath().replace('\\', '/');
            report.append(String.format(
                    "-  <%s|%s#%s> %s\n",
                    codeTodo.getContainingGitCheckout().getSourceControlLinkFormatter().build(filename, codeTodo.getLineNumber()),
                    codeTodo.getPosixPath(),
                    codeTodo.getLineNumber(),
                    codeTodo.getLine()
            ));
        }

        slackClient.postMessage(report.toString());
    }
}
