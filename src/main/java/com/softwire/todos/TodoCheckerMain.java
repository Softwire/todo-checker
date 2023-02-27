package com.softwire.todos;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.softwire.todos.jira.JiraClient;
import com.softwire.todos.jira.JiraCommenter;
import com.softwire.todos.reporter.FileReporter;
import com.softwire.todos.reporter.Reporter;
import com.softwire.todos.reporter.SlackReporter;
import com.softwire.todos.slack.SlackClient;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class TodoCheckerMain {

    /**
     * CLI entry point to the `TodoChecker` tool.
     */
    public static void main(String[] args) throws Exception {
        Logger log = LoggerFactory.getLogger(TodoCheckerMain.class);
        TodoCheckerReturnCode returnCode;

        TodoCheckerConfig config = new TodoCheckerConfig();
        CmdLineParser parser = new CmdLineParser(config);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            log.error(e.getMessage());
            parser.printUsage(System.err);
            System.exit(TodoCheckerReturnCode.INCORRECT_CLI_ARG.getValue());
        }

        config.applyDefaults();
        TodoCheckerApp app = todoCheckerApp(config);

        try {
            if(app.run()) {
                returnCode = TodoCheckerReturnCode.SUCCESS;
            } else {
                returnCode = TodoCheckerReturnCode.FOUND_INAPPROPRIATE_TODOS;
            }
        } catch (RestClientException e) {
            if (e.getMessage().contains("Client response status: 401")) {
                log.error(
                        "NOTE: for Cloud Jenkins, you will need to create an API key for your account " +
                        "and pass that as your password.  Visit https://id.atlassian.com/manage/api-tokens"
                );
            }
            log.error("Unexpected RestClientException", e);
            returnCode = TodoCheckerReturnCode.ERROR;
        } catch (Exception e) {
            log.error("Unexpected Exception", e);
            returnCode = TodoCheckerReturnCode.ERROR;
        }

        System.exit(returnCode.getValue());
    }

    /**
     * Manually construct the TodoCheckerApp via dependency injection
     */
    private static TodoCheckerApp todoCheckerApp(TodoCheckerConfig config) throws URISyntaxException {
        JiraClient jiraClient = new JiraClient(config);

        ArrayList<Reporter> reporters = new ArrayList<>();
        if (config.reportFile != null) {
            reporters.add(new FileReporter(Paths.get(config.reportFile), jiraClient));
        }
        if (config.slackChannel != null) {
            reporters.add(new SlackReporter(new SlackClient(config), jiraClient));
        }
        JiraCommenter jiraCommenter = new JiraCommenter(config, jiraClient);

        List<TodoFinder> todoFinders = config.srcDirs.stream().map(
                srcDir -> {
                    File srcDirFile = new File(srcDir);
                    checkArgument(srcDirFile.isDirectory(), "Invalid --src argument: " + srcDir);
                    GitCheckout gitCheckout;
                    try {
                        gitCheckout = new GitCheckout(srcDirFile, config);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return new TodoFinder(gitCheckout);
                })
                .collect(Collectors.toList());

        return new TodoCheckerApp(config, jiraClient, reporters, jiraCommenter, todoFinders);
    }

}
