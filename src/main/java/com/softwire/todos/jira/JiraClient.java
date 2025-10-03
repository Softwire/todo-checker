package com.softwire.todos.jira;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.ServerInfo;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A thin wrapper around {@link JiraRestClient} which
 * a) enforces the `config.getWriteToJira()` flag
 * b) unwraps some hidden functions
 * c) caches issues to prevent re-fetching the same data
 */
public class JiraClient {

    private final Config config;
    private final JiraRestClient restClient;
    private final Map<String, Issue> issuesByKey = new HashMap<>();
    private final Logger log = LoggerFactory.getLogger(getClass());
    private ServerInfo serverInfo;

    public JiraClient(Config config) throws URISyntaxException {
        this.config = config;
        URI serverUri = new URI(config.getJiraUrl());

        restClient = new AsynchronousJiraRestClientFactory()
                .createWithBasicHttpAuthentication(
                        serverUri,
                        config.getJiraUsername(),
                        config.getJiraPassword());
    }

    public ServerInfo getServerInfo() {
        if (serverInfo == null) {
            try {
                serverInfo = restClient.getMetadataClient().getServerInfo().get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return serverInfo;
    }

    public Issue getIssue(String key) throws Exception {
        if (config.getRestrictToSingleCardId() != null) {
            checkArgument(config.getRestrictToSingleCardId().equals(key));
        }

        try {
            Issue cached = issuesByKey.get(key);
            if (cached == null) {
                log.debug("Fetching card info for {}", key);
                cached = restClient.getIssueClient().getIssue(key).get();
                issuesByKey.put(key, cached);
            }
            return cached;
        } catch (Exception e) {
            throw new IOException("Unable to fetch issue " + key, e);
        }
    }

    public void addComment(Issue issue, Comment comment) throws Exception {
        if (config.getWriteToJira()) {
            log.info("Adding comment to {}", issue.getKey());
            restClient.getIssueClient()
                    .addComment(issue.getCommentsUri(), comment)
                    .get();
        } else {
            log.info("Dry-run mode: Would have added comment to {}:\n{}", issue.getKey(), comment.getBody());
        }
    }

    public void updateComment(Issue issue, Comment comment) throws Exception {
        if (config.getWriteToJira()) {
            log.info("Updating comment on {}", issue.getKey());

            restClient.getIssueClient()
                    .updateComment(comment)
                    .get();
        } else {
            log.info("Dry-run mode: Would have updated comment on {}:\n{}", issue.getKey(), comment.getBody());
        }
    }

    public void deleteComment(Issue issue, Comment comment) throws Exception {
        if (config.getWriteToJira()) {
            log.info("Deleting comment on {}", issue.getKey());

            restClient.getIssueClient()
                    .deleteComment(comment)
                    .get();
        } else {
            log.info("Dry-run mode: Would have deleted comment on {}:\n{}", issue.getKey(), comment.getBody());
        }
    }

    public Set<Issue> searchIssuesWithComments(String jql) throws Exception {
        SearchResult searchResult = restClient.getSearchClient()
                .searchJql(jql, 1000, null, ImmutableSet.of("comment", "status")).get();

        if (!searchResult.isLast()) {
            // If this occurs, it means we can't fetch all matching Issues in one page.
            // You'll need to either extend this app (and possibly the rest client lib) to
            // support pagination, or accept that some TODO comments will not get removed
            // when the TODO is fixed.
            // This is unlikely and should be self healing as the TODOs get fixed, so just warn:
            log.warn("Too many results from JIRA query: consider adding pagination");
        }

        Set<Issue> issues = new LinkedHashSet<>();
        for (Issue issue : searchResult.getIssues()) {
            if (config.getRestrictToSingleCardId() == null || issue.getKey().equals(config.getRestrictToSingleCardId())) {
                issues.add(issue);
            }
        }
        return issues;
    }

    public String getViewUrl(Issue issue) throws Exception {
        return new URI(config.getJiraUrl()).resolve("browse/" + issue.getKey()).toString();
    }

    public interface Config {
        String getRestrictToSingleCardId();

        boolean getWriteToJira();

        String getJiraUrl();

        String getJiraUsername();

        String getJiraPassword();
    }
}
