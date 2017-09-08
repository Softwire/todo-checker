package com.softwire.todos;

import com.atlassian.jira.rest.client.domain.BasicResolution;
import com.atlassian.jira.rest.client.domain.Issue;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * CLI entry point to the `TodoChecker` tool.
 */
public class TodoCheckerMain
        implements JiraClient.Config, JiraCommenter.Config {

    /// Command-line arguments

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
            usage = "The directory to scan for TODOs (must be in a git checkout)",
            required = true)
    public String srcDir;

    @Option(name = "--jira-url",
            usage = "The base url for jira with trailing slash, defaults to https://jira.softwire.com/jira/",
            required = false)
    public String jiraUrl = "https://jira.softwire.com/jira/";

    @Option(name = "--jira-project-key",
            usage = "The project key for JIRA, e.g. AAA, INTRO, BBC, PROJECTX, etc",
            required = true)
    public String jiraProjectKey;

    @Option(name = "--jira-project-key-in-todo-regex",
            usage = "A regex for project key as used in a todo, which doesn't need to match the jira key",
            required = true)
    public String jiraProjectKeyInTodoRegex;

    @Option(name = "--github-url",
            usage = "The url of the project in github, e.g. https://github.com/softwire/todo-checker",
            required = true)
    public String githubUrl;

    @Option(name = "--jira-username",
            usage = "The username of the Jira user who will comment on Jira tickets, e.g. sjw",
            required = true)
    public String jiraUsername;

    @Option(name = "--jira-password",
            usage = "The password of the Jira user who will comment on Jira tickets",
            required = true)
    public String jiraPassword;

    @Option(name = "--exclude-path-regex",
            usage = "Any paths to exclude, by regex, e.g. '^(node_modules/|broken-code/)'",
            required = false)
    public String excludePathRegex;

    /// End config

    private JiraClient jiraClient;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public TodoCheckerMain() throws URISyntaxException {
    }

    public static void main(String[] args) throws Exception {
        TodoCheckerMain app = new TodoCheckerMain();
        CmdLineParser parser = new CmdLineParser(app);
        boolean success;
        try {
            parser.parseArgument(args);
            success = app.run();
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            success = false;
        }

        System.exit(success ? 0 : 1);
    }

    private boolean run() throws Exception {
        this.jiraClient = new JiraClient(this);

        boolean success = true;

        if (!writeToJira) {
            log.info("This script will not write to JIRA unless you pass '--write-to-jira' " +
                    "as a command-line argument");
        }

        File srcDirFile = new File(this.srcDir);
        checkArgument(srcDirFile.isDirectory(), "Invalid --src argument");

        List<CodeTodo> allTodos = new TodoFinder(srcDirFile).findAllTodosInSource(this.excludePathRegex);

        log.info("{} code TODOs found:\n{}",
                allTodos.size(),
                Joiner.on("\n").join(allTodos));

        Multimap<Issue, CodeTodo> todosByIssue = groupTodosByJiraIssue(allTodos);

        new JiraCommenter(this, jiraClient).updateJiraComments(todosByIssue);

        success &= findTodosOnClosedCards(todosByIssue);

        success &= findTodosWithoutACardNumber(todosByIssue);

        return success;
    }

    /**
     * Sort the list into a multimap from JiraCard to CodeTodos.
     * Any CodeTodos with no card will be included at the "null" key.
     */
    private Multimap<Issue, CodeTodo> groupTodosByJiraIssue(List<CodeTodo> allTodos) throws Exception {
        HashMultimap<Issue, CodeTodo> acc = HashMultimap.create();
        Pattern cardNumberPat = Pattern.compile(
                jiraProjectKeyInTodoRegex + "[-_:]([0-9]+)",
                Pattern.CASE_INSENSITIVE);
        for (CodeTodo codeTodo : allTodos) {
            Matcher matcher = cardNumberPat.matcher(codeTodo.getLine());
            if (matcher.find()) {
                String id = jiraProjectKey + "-" + matcher.group(1);
                if (null == restrictToSingleCardId || id.equals(restrictToSingleCardId)) {
                    Issue issue = jiraClient.getIssue(id);
                    acc.put(issue, codeTodo);
                }
            } else {
                if (null == restrictToSingleCardId) {
                    acc.put(null, codeTodo);
                }
            }
        }

        return acc;
    }

    private boolean findTodosOnClosedCards(Multimap<Issue, CodeTodo> todosByIssue) {
        boolean ok = true;
        for (Map.Entry<Issue, Collection<CodeTodo>> entry : todosByIssue.asMap().entrySet()) {
            Issue issue = entry.getKey();
            if (issue == null) {
                continue;
            }

            BasicResolution resolution = issue.getResolution();
            if (resolution != null) {
                log.error("TODOs on a resolved '{}' JIRA card found {}",
                        resolution.getName(),
                        issue.getKey());
                for (CodeTodo codeTodo : entry.getValue()) {
                    log.error("  {}:{} {}",
                            codeTodo.getFile(),
                            codeTodo.getLineNumber(),
                            codeTodo.getLine());
                }
                ok = false;
            }

            switch (issue.getStatus().getName()) {
                case "In Test":
                case "Passed test":
                case "UAT":
                case "Done":
                    log.error("TODOs on a JIRA card with status '{}': {}",
                            issue.getStatus().getName(),
                            issue.getKey());
                    for (CodeTodo codeTodo : entry.getValue()) {
                        log.error("  {}:{} {}",
                                codeTodo.getFile(),
                                codeTodo.getLineNumber(),
                                codeTodo.getLine());
                    }
                    ok = false;
            }
        }
        return ok;
    }

    private boolean findTodosWithoutACardNumber(Multimap<Issue, CodeTodo> todosByIssue) {
        Collection<CodeTodo> codeTodos = todosByIssue.get(null);
        if (codeTodos.isEmpty()) {
            return true;
        }
        log.error("TODOs without a JIRA card found:");
        for (CodeTodo codeTodo : codeTodos) {
            log.error("  {}:{} {}",
                    codeTodo.getFile(),
                    codeTodo.getLineNumber(),
                    codeTodo.getLine());
        }
        return false;
    }

    @Override
    public String getRestrictToSingleCardId() {
        return restrictToSingleCardId;
    }

    @Override
    public boolean getWriteToJira() {
        return writeToJira;
    }

    @Override
    public String getJiraProjectKey() {
        return jiraProjectKey;
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
    public String getJiraUsername() {
        return jiraUsername;
    }

    @Override
    public String getJiraPassword() {
        return jiraPassword;
    }
}
