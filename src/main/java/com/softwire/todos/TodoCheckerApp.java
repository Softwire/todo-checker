package com.softwire.todos;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Resolution;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.softwire.todos.errors.*;
import com.softwire.todos.jira.JiraClient;
import com.softwire.todos.jira.JiraCommenter;
import com.softwire.todos.jira.JiraProject;
import com.softwire.todos.reporter.Reporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The high-level application logic for the `TodoChecker` tool.
 */
public class TodoCheckerApp {
    private final Config config;
    private final JiraClient jiraClient;
    private final List<Reporter> reporters;
    private final JiraCommenter jiraCommenter;
    private final List<TodoFinder> todoFinders;

    private final Logger log = LoggerFactory.getLogger(getClass());

    public TodoCheckerApp(Config config,
                          JiraClient jiraClient,
                          ArrayList<Reporter> reporters,
                          JiraCommenter jiraCommenter,
                          List<TodoFinder> todoFinders) {
        this.config = config;
        this.jiraClient = jiraClient;
        this.reporters = reporters;
        this.jiraCommenter = jiraCommenter;
        this.todoFinders = todoFinders;
    }

    public boolean run() throws Exception {
        if (!config.getWriteToJira()) {
            log.info("This script will not write to JIRA unless you pass '--write-to-jira' " +
                     "as a command-line argument");
        }

        log.info("Connected to JIRA, server build number = {}", jiraClient.getServerInfo().getBuildNumber());

        List<CodeTodo> allTodos = todoFinders.stream()
            .flatMap(todoFinder -> {
                try {
                    return todoFinder.findAllTodosInSource(config.getExcludePathRegex()).stream();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());

        log.info("{} code TODOs found", allTodos.size());
        log.debug(Joiner.on("\n").join(allTodos));

        Multimap<JiraIssueReference, CodeTodo> todosByIssue = groupTodosByJiraIssue(allTodos);

        jiraCommenter.updateJiraComments(todosByIssue);

        TodoCheckerErrors errors = TodoCheckerErrors.empty();
        findTodosOnClosedCards(todosByIssue, errors);
        findTodosWithoutACardNumber(todosByIssue, errors);
        findTodosWithNonExistentIssues(todosByIssue, errors);

        for (Reporter reporter : reporters) {
            reporter.report(errors);
        }

        return errors.isSuccess();
    }

    /**
     * Sort the list into a multimap from JiraCard to CodeTodos.
     * <p>
     * Any CodeTodos with no card will be included at the "null" key.
     * <p>
     * Any CodeTodos against ignored projects will not be returned
     */
    private Multimap<JiraIssueReference, CodeTodo> groupTodosByJiraIssue(List<CodeTodo> allTodos) throws Exception {
        HashMultimap<JiraIssueReference, CodeTodo> acc = HashMultimap.create();

        List<Pattern> jiraProjectPatterns = config.getJiraProjects().stream()
            .map(JiraProject::getIssueIdPattern)
            .collect(Collectors.toList());
        List<Pattern> ignoredJiraProjectPatterns = config.getIgnoredJiraProjects().stream()
            .map(JiraProject::getIssueIdPattern)
            .collect(Collectors.toList());

        Map<String, JiraIssueReference> issueReferencesByKey = new HashMap<>();

        outer:
        for (CodeTodo codeTodo : allTodos) {
            String id = null;
            // Check if the item matches a jira project
            for (int i = 0; i < config.getJiraProjects().size(); i++) {
                Matcher matcher = jiraProjectPatterns.get(i).matcher(codeTodo.getLine());
                if (matcher.find()) {
                    String idGroup = matcher.group("id");
                    checkNotNull(idGroup);
                    id = config.getJiraProjects().get(i).getKey() + "-" + idGroup;
                    break;
                }
            }
            // Else check if it matches an ignored jira project
            if (id == null) {
                for (Pattern pattern : ignoredJiraProjectPatterns) {
                    Matcher matcher = pattern.matcher(codeTodo.getLine());
                    if (matcher.find()) {
                        log.debug("Ignoring code TODO against {}: {}", matcher.group(), codeTodo);
                        continue outer;
                    }
                }
            }

            // Now add it to the multimap
            if (id != null) {
                if (null == config.getRestrictToSingleCardId() || id.equals(config.getRestrictToSingleCardId())) {
                    JiraIssueReference issueReference = issueReferencesByKey.get(id);
                    if (issueReference == null) {
                        Issue issue = null;
                        try {
                            issue = jiraClient.getIssue(id);
                        } catch (IOException e) {
                            if (null != e.getCause()
                                    && e.getCause().getCause() instanceof RestClientException
                                    && ((RestClientException) e.getCause().getCause()).getStatusCode().isPresent()
                                    && ((RestClientException) e.getCause().getCause()).getStatusCode().get() == (404)
                            ) {
                                log.warn("Could not find issue {}", id);
                            } else {
                                throw e;
                            }
                        }
                        issueReference = new JiraIssueReference(id, issue);
                        issueReferencesByKey.put(id, issueReference);
                    }
                    acc.put(issueReference, codeTodo);
                }
            } else {
                if (null == config.getRestrictToSingleCardId()) {
                    acc.put(null, codeTodo);
                }
            }
        }

        return acc;
    }

    private void findTodosOnClosedCards(
            Multimap<JiraIssueReference, CodeTodo> todosByIssue,
            TodoCheckerErrors errors) {

        for (Map.Entry<JiraIssueReference, Collection<CodeTodo>> entry : todosByIssue.asMap().entrySet()) {
            Issue issue = entry.getKey().getIssue();
            if (issue == null) {
                continue;
            }

            Resolution resolution = issue.getResolution();
            if (resolution != null) {
                log.error(
                        String.format("TODOs on a resolved '%s' JIRA card found %s",
                        resolution.getName(),
                        issue.getKey()
                ));
                logTodos(entry.getValue());
                errors.getWithResolvedCardErrors().add(new WithResolvedCardError(entry.getValue(), issue, resolution.getName()));
            }

            if (config.getInvalidCardStatuses().contains(issue.getStatus().getName())) {
                    log.error(String.format(
                            "TODOs on a JIRA card with status '%s': %s",
                            issue.getStatus().getName(),
                            issue.getKey()
                    ));
                logTodos(entry.getValue());
                errors.getWithInvalidStatusErrors().add(new WithInvalidStatusError(entry.getValue(), issue, issue.getStatus().getName()));
            }
        }
    }

    private void findTodosWithoutACardNumber(
            Multimap<JiraIssueReference, CodeTodo> todosByIssue,
            TodoCheckerErrors errors) {
        Collection<CodeTodo> codeTodos = todosByIssue.get(null);
        if (codeTodos.isEmpty()) {
            return;
        }

        log.error("TODOs without a JIRA card found");
        logTodos(codeTodos);
        errors.getWithoutCardErrors().add(new WithoutCardError(codeTodos));
    }

    private void findTodosWithNonExistentIssues(
            Multimap<JiraIssueReference, CodeTodo> todosByIssue,
            TodoCheckerErrors errors) {
        Collection<CodeTodo> codeTodos = todosByIssue.asMap().entrySet().stream()
                .filter(e -> null != e.getKey().getId() && null == e.getKey().getIssue())
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        if (codeTodos.isEmpty()) {
            return;
        }

        log.error("TODOs referencing non-existent JIRA cards found");
        logTodos(codeTodos);
        errors.getWithNonExistentCardErrors().add(new WithNonExistentCardError(codeTodos));
    }


    private void logTodos(Collection<CodeTodo> entry) {
        for (CodeTodo error : entry) {
            log.error(String.format("   %s:%s %s", error.getPosixPath(), error.getLineNumber(), error.getLine()));
        }
    }

    public interface Config {
        List<String> getInvalidCardStatuses();
        List<JiraProject> getJiraProjects();
        String getRestrictToSingleCardId();
        List<JiraProject> getIgnoredJiraProjects();
        String getExcludePathRegex();
        boolean getWriteToJira();
    }
}
