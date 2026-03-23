package com.softwire.todos;

import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.softwire.todos.jira.JiraClient;
import com.softwire.todos.jira.JiraCommenter;
import com.softwire.todos.jira.JiraProject;
import com.softwire.todos.jira.JiraProjectOptionHandler;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Run this class to delete all TODO comments from JIRA issues.
 *
 * --job-name will be respected
 */
public class DeleteAllTodoCommentsTool {
    public static void main(String[] args) throws Exception {
        Logger log = LoggerFactory.getLogger(DeleteAllTodoCommentsTool.class);

        DeleteToolConfig config = new DeleteToolConfig();
        CmdLineParser parser = new CmdLineParser(config);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            log.error(e.getMessage());
            parser.printUsage(System.err);
            System.exit(TodoCheckerReturnCode.INCORRECT_CLI_ARG.getValue());
        }

        JiraClient jiraClient = new JiraClient(config);
        JiraCommenter jiraCommenter = new JiraCommenter(config, jiraClient);
        for (Issue issue : jiraCommenter.findAllIssuesWithTodoComments()) {
            Comment comment = jiraCommenter.findTodoComment(issue);
            if (comment != null) {
                if (comment.getAuthor().getAccountId().equals(config.actingUserAccountId)) {
                    jiraClient.deleteComment(issue, comment);
                } else {
                    log.warn("{}: Not deleting comment by {}",
                            issue.getKey(),
                            comment.getAuthor().getDisplayName());
                }
            }
        }
    }

    public static class DeleteToolConfig implements JiraClient.Config, JiraCommenter.Config {
        @Option(name = "--write-to-jira",
                usage = "Unless this is set, no changes will be made in JIRA")
        public boolean writeToJira = false;

        @Option(name = "--acting-user-account-id",
                usage = "The JIRA account ID of the user whose comments should be deleted. " +
                        "This is required to prevent accidentally deleting other people's comments.",
                required = true)
        public String actingUserAccountId;

        @Option(name = "--jira-url",
                usage = "The base url for jira with trailing slash, defaults to https://jira.softwire.com/jira/",
                required = false)
        public String jiraUrl = "https://jira.softwire.com/jira/";

        @Option(name = "--only-card",
                usage = "Set this to a JIRA card id to only operate on that one card.")
        public String restrictToSingleCardId = null;

        @Option(name = "--jira-username",
                usage = "The username of the Jira user who will comment on Jira tickets, e.g. sjw",
                required = true)
        public String jiraUsername;

        @Option(name = "--jira-password",
                usage = "The password of the Jira user who will comment on Jira tickets",
                required = true)
        public String jiraPassword;

        @Option(name = "--jira-project-key",
                usage = "The project key for JIRA, e.g. AAA, INTRO, PROJECTX, etc.  Pass this flag multiple times for " +
                        "multiple projects.  If you need to use a regex other than the project key when looking for the " +
                        "card key in a todo comment, then pass it here with an \"=\". For example if your JIRA project " +
                        "key is something long like COMPANY-DEPT-FOO but your team writes TODOs like " +
                        "\"TODO:FOO-123\", then pass \"--jira-project COMPANY-DEPT-FOO=FOO\"\n" +
                        "You can also use a regex, e.g. \"--jira-project COMPANY-DEPT-FOO=FOO|DEPT-FOO\"",
                required = true,
                handler = JiraProjectOptionHandler.class)
        public List<JiraProject> jiraProjects = new ArrayList<>();

        @Option(name = "--job-name",
                usage = "Job name.  This will be prefixed to all JIRA comments.  You must set this to a unique value if " +
                        "you have multiple jobs running against different codebases but with the same JIRA project, " +
                        "otherwise the jobs will interfere with each other.")
        public String jobName = null;

        public boolean getWriteToJira() {
            return writeToJira;
        }

        public String getActingUserAccountId() {
            return actingUserAccountId;
        }

        @Override
        public String getJiraUrl() {
            return jiraUrl;
        }

        @Override
        public String getRestrictToSingleCardId() {
            return restrictToSingleCardId;
        }

        @Override
        public String getJiraUsername() {
            return jiraUsername;
        }

        @Override
        public String getJiraPassword() {
            return jiraPassword;
        }

        @Override
        public List<JiraProject> getJiraProjects() {
            return jiraProjects;
        }

        @Override
        public String getJobName() {
            return jobName;
        }
    }
}
