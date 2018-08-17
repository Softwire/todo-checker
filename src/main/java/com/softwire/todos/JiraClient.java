package com.softwire.todos;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.ProgressMonitor;
import com.atlassian.jira.rest.client.domain.*;
import com.atlassian.jira.rest.client.internal.jersey.AbstractJerseyRestClient;
import com.atlassian.jira.rest.client.internal.jersey.JerseyIssueRestClient;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;
import com.atlassian.jira.rest.client.internal.jersey.JerseySearchRestClient;
import com.atlassian.jira.rest.client.internal.json.JsonObjectParser;
import com.atlassian.jira.rest.client.internal.json.SearchResultJsonParser;
import com.atlassian.jira.rest.client.internal.json.gen.CommentJsonGenerator;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private Map<String, Issue> issuesByKey = new HashMap<>();
    private final Logger log = LoggerFactory.getLogger(getClass());
    private ServerInfo serverInfo;

    public JiraClient(Config config) throws URISyntaxException {
        this.config = config;
        URI serverUri = new URI(config.getJiraUrl());

        restClient = new JerseyJiraRestClientFactory()
                .createWithBasicHttpAuthentication(serverUri, config.getJiraUsername(), config.getJiraPassword());
    }

    public ServerInfo getServerInfo() {
        if (serverInfo == null) {
            serverInfo = restClient.getMetadataClient().getServerInfo(null);
        }
        return serverInfo;
    }

    public Issue getIssue(String key) throws Exception {
        if (config.getRestrictToSingleCardId() != null) {
            checkArgument(config.getRestrictToSingleCardId().equals(key));
        }

        Issue cached = issuesByKey.get(key);
        if (cached == null) {
            log.debug("Fetching card info for {}", key);
            cached = restClient.getIssueClient().getIssue(key, null);
            issuesByKey.put(key, cached);
        }
        return cached;
    }

    public void addComment(Issue issue, Comment comment) {
        if (config.getWriteToJira()) {
            log.info("Adding comment to {}", issue.getKey());
            restClient.getIssueClient()
                    .addComment(null, issue.getCommentsUri(), comment);
        } else {
            log.info("Not adding comment to {}:\n{}", issue.getKey(), comment.getBody());
        }
    }

    public void updateComment(
            Issue issue,
            Comment oldComment,
            Comment newComment) throws Exception {
        if (config.getWriteToJira()) {
            log.info("Updating comment on {}", issue.getKey());

            // The public interface doesn't offer an "update" call:
            JerseyIssueRestClient client = (JerseyIssueRestClient) restClient.getIssueClient();

            // c.f. AbstractJerseyRestClient#post
            WebResource webResource = getApacheClient(client)
                    .resource(oldComment.getSelf());
            JSONObject body = new CommentJsonGenerator(
                    getServerInfo(client)).generate(newComment);
            webResource.put(body);
        } else {
            log.info("Not updating comment to {}:\n{}", issue.getKey(), newComment.getBody());
        }
    }

    public void deleteComment(Issue issue, Comment comment) throws Exception {
        if (config.getWriteToJira()) {
            log.info("Deleting comment on {}", issue.getKey());

            // The public interface doesn't offer a "delete" call:
            JerseyIssueRestClient client = (JerseyIssueRestClient) restClient.getIssueClient();

            // c.f. AbstractJerseyRestClient#post
            WebResource webResource = getApacheClient(client)
                    .resource(comment.getSelf());
            webResource.delete();
        } else {
            log.info("Not deleting comment to {}:\n{}", issue.getKey(), comment.getBody());
        }
    }

    public Set<Issue> searchJqlWithFullIssues(String jql) throws Exception {
        // The public implementation in SearchRestClient doesn't let us request
        // the comments, so we'll unwrap the innards:
        JerseySearchRestClient client = (JerseySearchRestClient) restClient.getSearchClient();
        UriBuilder uriBuilder = UriBuilder
                .fromUri(getSearchUri(client))
                .queryParam("jql", jql);
        uriBuilder = uriBuilder.queryParam("maxResults", 1000);
        URI uri = uriBuilder.queryParam("fields", "*all").build();

        SearchResult searchResult = getAndParse(
                client,
                uri,
                new SearchResultJsonParser(true),
                null);

        Set<Issue> issues = new LinkedHashSet<>();
        for (BasicIssue issue : searchResult.getIssues()) {
            if (config.getRestrictToSingleCardId() == null || issue.getKey().equals(config.getRestrictToSingleCardId())) {
                issues.add((Issue) issue);
            }
        }
        return issues;
    }

    public String getViewUrl(Issue issue) throws Exception {
        return new URI(config.getJiraUrl()).resolve("browse/" + issue.getKey()).toString();
    }

    private SearchResult getAndParse(
            AbstractJerseyRestClient client,
            URI uri,
            SearchResultJsonParser searchResultJsonParser,
            ProgressMonitor progressMonitor)
            throws Exception {
        Method method = AbstractJerseyRestClient.class.getDeclaredMethod(
                "getAndParse",
                URI.class,
                JsonObjectParser.class,
                ProgressMonitor.class);
        method.setAccessible(true);
        return (SearchResult) method.invoke(client, uri, searchResultJsonParser, progressMonitor);
    }

    private URI getSearchUri(JerseySearchRestClient client) throws Exception {
        Field field = JerseySearchRestClient.class.getDeclaredField("searchUri");
        field.setAccessible(true);
        return (URI) field.get(client);
    }

    private ServerInfo getServerInfo(JerseyIssueRestClient client) throws Exception {
        Field field = JerseyIssueRestClient.class.getDeclaredField("serverInfo");
        field.setAccessible(true);
        return (ServerInfo) field.get(client);
    }

    private ApacheHttpClient getApacheClient(AbstractJerseyRestClient client) throws Exception {
        Field field = AbstractJerseyRestClient.class.getDeclaredField("client");
        field.setAccessible(true);
        return (ApacheHttpClient) field.get(client);
    }

    public interface Config {
        String getRestrictToSingleCardId();
        boolean getWriteToJira();
        String getJiraUrl();
        String getJiraUsername();
        String getJiraPassword();
    }
}
