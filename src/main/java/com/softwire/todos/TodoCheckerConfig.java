package com.softwire.todos;

import com.softwire.todos.jira.JiraClient;
import com.softwire.todos.jira.JiraCommenter;
import com.softwire.todos.jira.JiraProject;
import com.softwire.todos.jira.JiraProjectOptionHandler;
import com.softwire.todos.slack.SlackClient;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TodoCheckerConfig implements JiraClient.Config, JiraCommenter.Config, GitCheckout.Config, SlackClient.Config, TodoCheckerApp.Config {
    private static final List<String> DEFAULT_INVALID_CARD_STATUSES = Arrays.asList("In Test", "Passed test", "UAT", "Done");

    @Option(name = "--write-to-jira",
            usage = "Unless this is set, no changes will be made in JIRA")
    public boolean writeToJira = false;

    /**
     * If this is set, only the specified card will be acted on
     */
    @Option(name = "--only-card",
            usage = "Set this to a JIRA card id to only operate on that one card.")
    public String restrictToSingleCardId = null;

    @Option(name = "--src",
            usage = "The directory to scan for TODOs (must be in a git checkout). " +
                    "Pass this arg multiple times for multiple projects.",
            required = true)
    public List<String> srcDirs;

    @Option(name = "--jira-url",
            usage = "The base url for jira with trailing slash, defaults to https://jira.softwire.com/jira/",
            required = false)
    public String jiraUrl = "https://jira.softwire.com/jira/";

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

    @Option(name = "--ignore-jira-project-key",
            usage = "A JIRA project key to ignore, e.g. AAA, INTRO, PROJECTX, etc. Pass this flag multiple times for " +
                    "multiple projects. You can use this if your project uses multiple JIRA instances. Any TODOs which " +
                    "reference this JIRA project will not count as untracked TODOs, but no JIRA comments will be updated " +
                    "for them.",
            handler = JiraProjectOptionHandler.class)
    public List<JiraProject> ignoredJiraProjects = new ArrayList<>();

    @Option(name = "--github-url",
            usage = "OPTIONAL (omit this to auto-detect, especially if you are scanning multiple checkouts). " +
                    "The url of the project in github, e.g. https://github.com/softwire/todo-checker. " +
                    "GitLab uses the same URL format, so use this arg if you use GitLab.",
            forbids = "--gitblit-url")
    public String githubUrl;

    @Option(name = "--gitblit-url",
            usage = "The url of the project in gitblit, e.g. https://example.com/gitblit?r=todo-checker.git",
            forbids = "--github-url")
    public String gitblitUrl;

    @Option(name = "--jira-username",
            usage = "The username of the Jira user who will comment on Jira tickets, e.g. sjw",
            required = true)
    public String jiraUsername;

    @Option(name = "--jira-password",
            usage = "The password of the Jira user who will comment on Jira tickets",
            required = true)
    public String jiraPassword;


    // args4j doesn't provide a way to default a multivalued field: if you provide a default here then any further
    // values from CLI arguments be added to the field, rather than replacing it.  Hence, we have to do the defaulting
    // later.
    @Option(name = "--invalid-card-status",
            usage = "If a TODO is found against a JIRA card with one of these statuses, the TODO checker will report" +
                    "an error.  By default these are \"In Test\", \"Passed test\", \"UAT\", and \"Done\".")
    public List<String> invalidCardStatuses = null;

    @Option(name = "--exclude-path-regex",
            usage = "Any paths to exclude, by regex, e.g. '^(node_modules/|broken-code/)'",
            required = false)
    public String excludePathRegex;

    @Option(name = "--job-name",
            usage = "Job name.  This will be prefixed to all JIRA comments.  You must set this to a unique value if " +
                    "you have multiple jobs running against different codebases but with the same JIRA project, " +
                    "otherwise the jobs will interfere with each other.")
    public String jobName = null;

    @Option(name = "--report-file",
            usage = "Write report of errors to this file as well as to the console.")
    public String reportFile = null;

    @Option(name = "--slack-channel",
            usage = "Post report of errors to this slack channel as well as to the console.",
            depends={"--slack-token"})
    public String slackChannel = null;

    @Option(name = "--slack-token",
            usage = "Slack Token used to Authenticate with Slack API.",
            depends={"--slack-channel"})
    public String slackToken = null;

    public void applyDefaults() {
        // We have to provide the default ourselves for the invalidCardStatuses, see comment by the @Option.
        if (invalidCardStatuses == null) {
            invalidCardStatuses = DEFAULT_INVALID_CARD_STATUSES;
        }
    }

    @Override
    public String getJobName() {
        return jobName;
    }

    @Override
    public String getRestrictToSingleCardId() {
        return restrictToSingleCardId;
    }

    @Override
    public List<JiraProject> getIgnoredJiraProjects() {
        return ignoredJiraProjects;
    }

    @Override
    public String getExcludePathRegex() {
        return excludePathRegex;
    }

    @Override
    public boolean getWriteToJira() {
        return writeToJira;
    }

    @Override
    public List<String> getInvalidCardStatuses() {
        return invalidCardStatuses;
    }

    @Override
    public List<JiraProject> getJiraProjects() {
        return jiraProjects;
    }

    @Override
    public String getGithubUrl() {
        return githubUrl;
    }

    @Override
    public String getJiraUrl() {
        return jiraUrl;
    }

    @Override
    public String getGitblitUrl() {
        return gitblitUrl;
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
    public String getSlackChannel() {
        return slackChannel;
    }

    @Override
    public String getSlackToken() {
        return slackToken;
    }
}
