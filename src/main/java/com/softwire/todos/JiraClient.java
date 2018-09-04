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
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.ApacheHttpClientHandler;
import com.sun.jersey.client.apache.ApacheHttpMethodExecutor;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URIException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

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
            .createWithBasicHttpAuthentication(
                serverUri,
                config.getJiraUsername(),
                config.getJiraPassword());

        // The default Jersey HTTP client does not appear to have any retry logic available.
        //
        // The underlying Apache Http client has retry logic, but only for IOExceptions,
        // not for HTTP status codes like 429.
        // Apache HTTP added status code based retry in ver 4, but we are stuck on v3 so
        // that we can use the v1 JIRA client, which is a lot easier to use than the
        // half-finished async v6 client api.
        //
        // We poke our override inside the client using reflection, as the public
        // extension points to do so are very cumbersome:
        ApacheHttpClient client = (ApacheHttpClient) readField(restClient, "client");
        ApacheHttpClientHandler clientHandler = (ApacheHttpClientHandler) readField(client, "clientHandler");
        ApacheHttpMethodExecutor methodExecutor = (ApacheHttpMethodExecutor) readField(clientHandler, "methodExecutor");
        writeField(clientHandler, "methodExecutor", new ApacheMethodExecutorWithRetryAfterSupport(methodExecutor));
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

    private static Object readField(Object o, String fieldName) {
        try {
            Class<?> clazz = o.getClass();
            Field field;
            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                field = clazz.getSuperclass().getDeclaredField(fieldName);
            }
            field.setAccessible(true);
            return field.get(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeField(Object o, String fieldName, Object val) {
        try {
            Field field = o.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(o, val);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public interface Config {
        String getRestrictToSingleCardId();

        boolean getWriteToJira();

        String getJiraUrl();

        String getJiraUsername();

        String getJiraPassword();
    }

    private static class ApacheMethodExecutorWithRetryAfterSupport
        implements ApacheHttpMethodExecutor {
        private final Logger log = LoggerFactory.getLogger(getClass());
        private final ApacheHttpMethodExecutor inner;
        private static final int MAX_RETRY_COUNT = 3;

        private ApacheMethodExecutorWithRetryAfterSupport(ApacheHttpMethodExecutor inner) {
            this.inner = inner;
        }

        @Override
        public void executeMethod(HttpMethod method, ClientRequest cr) {

            int attemptCount = 0;
            while (true) {
                inner.executeMethod(method, cr);
                Optional<Integer> retryDelaySec = getRetryDelaySec(attemptCount, method, cr);

                if (!retryDelaySec.isPresent()) {
                    return;
                } else {
                    try {
                        Thread.sleep(retryDelaySec.get() * 1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    // The method should now be re-usable in most cases.
                    //
                    // c.f. the retry loop in HttpMethodDirector.executeWithRetry
                    //
                    // If the request body is from a stream or similar, it will throw at
                    // executeMethod above
                    method.releaseConnection();
                }

                attemptCount++;
            }
        }

        private Optional<Integer> getRetryDelaySec(int attemptCount, HttpMethod method, ClientRequest cr) {

            // 429 TOO MANY REQUESTS
            // https://httpstatuses.com/429
            if (method.getStatusCode() == 429 && attemptCount < MAX_RETRY_COUNT) {
                Header retryAfterHeader = method.getResponseHeader("Retry-After");
                int retryDelaySec;
                if (retryAfterHeader != null) {
                    retryDelaySec =
                        Math.max(10,
                                 Math.min(600,
                                          Integer.parseInt(retryAfterHeader.getValue())));

                } else {
                    retryDelaySec = (30 * (attemptCount + 1));
                }

                String uri;
                try {
                    uri = method.getURI().toString();
                } catch (URIException e) {
                    uri = e.toString();
                }

                log.warn(
                    "Received a {} response from {}. " +
                    "Response Retry-After was {}. " +
                    "Pausing for {}s before retrying.",
                    method.getStatusLine(),
                    uri,
                    retryAfterHeader,
                    retryDelaySec);

                return Optional.of(retryDelaySec);
            } else {
                return Optional.empty();
            }
        }
    }
}
