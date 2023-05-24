package com.softwire.todos.reporter;

import com.softwire.todos.CodeTodo;
import com.softwire.todos.errors.TodoCheckerErrors;
import com.softwire.todos.errors.WithInvalidStatusError;
import com.softwire.todos.errors.WithResolvedCardError;
import com.softwire.todos.errors.WithoutCardError;
import com.softwire.todos.jira.JiraClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public class FileReporter implements Reporter {
    private final Path path;
    private final JiraClient jiraClient;

    public FileReporter(Path path, JiraClient jiraClient) {
        this.path = path;
        this.jiraClient = jiraClient;
    }

    @Override
    public void report(TodoCheckerErrors errors) throws Exception {
        Files.write(path, format(errors).getBytes(StandardCharsets.UTF_8));
    }

    private String format(TodoCheckerErrors errors) throws Exception {
        StringBuilder report = new StringBuilder();
        for (WithResolvedCardError error : errors.getWithResolvedCardErrors()) {
            report.append(String.format(
                    "TODOs on a resolved '%s' JIRA card found %s\n",
                    error.getResolutionName(),
                    jiraClient.getViewUrl(error.getIssue())
            ));
            appendCodeTodoDetails(error.getCodeTodos(), report);
        }

        for (WithInvalidStatusError error : errors.getWithInvalidStatusErrors()) {
            report.append(String.format(
                    "TODOs on a JIRA card with status '%s': %s\n",
                    error.getStatusName(),
                    jiraClient.getViewUrl(error.getIssue())
            ));
            appendCodeTodoDetails(error.getCodeTodos(), report);
        }

        for (WithoutCardError error: errors.getWithoutCardErrors()) {
            report.append("TODOs without a JIRA card found:\n");
            appendCodeTodoDetails(error.getCodeTodos(), report);
        }

        return report.toString();
    }

    private void appendCodeTodoDetails(Collection<CodeTodo> error, StringBuilder report) {
        for (CodeTodo codeTodo : error) {
            report.append(String.format(
                    "  %s:%s %s\n",
                    codeTodo.getFile(),
                    codeTodo.getLineNumber(),
                    codeTodo.getLine()
            ));
        }
    }
}
