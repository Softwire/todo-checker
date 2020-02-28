package com.softwire.todos;

import com.atlassian.httpclient.api.HttpClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.ServerInfo;
import com.atlassian.jira.rest.client.internal.async.AbstractAsynchronousRestClient;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.jira.rest.client.internal.async.AsynchronousSearchRestClient;
import com.atlassian.jira.rest.client.internal.json.JsonParser;
import com.atlassian.jira.rest.client.internal.json.SearchResultJsonParser;
import com.atlassian.jira.rest.client.internal.json.gen.CommentJsonGenerator;
import com.atlassian.jira.rest.client.internal.json.gen.JsonGenerator;
import io.atlassian.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

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

        restClient = new AsynchronousJiraRestClientFactory()
                .createWithBasicHttpAuthentication(
                        serverUri,
                        config.getJiraUsername(),
                        config.getJiraPassword());

        // qq
//        // The default Jersey HTTP client does not appear to have any retry logic available.
//        //
//        // The underlying Apache Http client has retry logic, but only for IOExceptions,
//        // not for HTTP status codes like 429.
//        // Apache HTTP added status code based retry in ver 4, but we are stuck on v3 so
//        // that we can use the v1 JIRA client, which is a lot easier to use than the
//        // half-finished async v6 client api.
//        //
//        // We poke our override inside the client using reflection, as the public
//        // extension points to do so are very cumbersome:
//        ApacheHttpClient client = (ApacheHttpClient) readField(restClient, "client");
//        ApacheHttpClientHandler clientHandler = (ApacheHttpClientHandler) readField(client, "clientHandler");
//        ApacheHttpMethodExecutor methodExecutor = (ApacheHttpMethodExecutor) readField(clientHandler, "methodExecutor");
//        writeField(clientHandler, "methodExecutor", new ApacheMethodExecutorWithRetryAfterSupport(methodExecutor));
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

        Issue cached = issuesByKey.get(key);
        if (cached == null) {
            log.debug("Fetching card info for {}", key);
            cached = restClient.getIssueClient().getIssue(key).get();
            issuesByKey.put(key, cached);
        }
        return cached;
    }

    public void addComment(Issue issue, Comment comment) throws Exception {
        if (config.getWriteToJira()) {
            log.info("Adding comment to {}", issue.getKey());
            restClient.getIssueClient()
                    .addComment(issue.getCommentsUri(), comment)
                    .get();
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
            AbstractAsynchronousRestClient client = (AbstractAsynchronousRestClient) restClient.getIssueClient();

            new ClientWrapper(client)
                    .put2(
                            oldComment.getSelf(),
                            newComment,
                            new CommentJsonGenerator(getServerInfo()))
                    .get();
        } else {
            log.info("Not updating comment to {}:\n{}", issue.getKey(), newComment.getBody());
        }
    }

    /**
     * This wrapper just exposes somes protected methods
     */
    private class ClientWrapper extends AbstractAsynchronousRestClient {
        public ClientWrapper(AbstractAsynchronousRestClient inner) throws Exception {
            super(getApacheClient(inner));
        }

        public <T> Promise<Void> put2(final URI uri, final T entity, final JsonGenerator<T> jsonGenerator) {
            return super.put(uri, entity, jsonGenerator);
        }

        public final Promise<Void> delete2(final URI uri) {
            return delete(uri);
        }

        public <T> Promise<T> getAndParse2(final URI uri, final JsonParser<?, T> parser) {
            return getAndParse(uri, parser);
        }
    }

    public void deleteComment(Issue issue, Comment comment) throws Exception {
        if (config.getWriteToJira()) {
            log.info("Deleting comment on {}", issue.getKey());

            // The public interface doesn't offer a "delete" call:
            AbstractAsynchronousRestClient client = (AbstractAsynchronousRestClient) restClient.getIssueClient();

            new ClientWrapper(client)
                    .delete2(comment.getSelf())
                    .get();
        } else {
            log.info("Not deleting comment to {}:\n{}", issue.getKey(), comment.getBody());
        }
    }

    public Set<Issue> searchJqlWithFullIssues(String jql) throws Exception {
        // The public implementation in SearchRestClient doesn't let us request
        // the comments, so we'll unwrap the innards:
        AsynchronousSearchRestClient client = (AsynchronousSearchRestClient) restClient.getSearchClient();
        UriBuilder uriBuilder = UriBuilder
                .fromUri(getSearchUri(client))
                .queryParam("jql", jql);
        uriBuilder = uriBuilder.queryParam("maxResults", 1000);
        URI uri = uriBuilder.queryParam("fields", "*all").build();

        SearchResult searchResult = new ClientWrapper(client)
                .getAndParse2(
                        uri,
                        new SearchResultJsonParser()).get();

        Set<Issue> issues = new LinkedHashSet<>();
        for (Issue issue : searchResult.getIssues()) {
            if (config.getRestrictToSingleCardId() == null || issue.getKey().equals(config.getRestrictToSingleCardId())) {
                issues.add((Issue) issue);
            }
        }
        return issues;
    }

    public String getViewUrl(Issue issue) throws Exception {
        return new URI(config.getJiraUrl()).resolve("browse/" + issue.getKey()).toString();
    }

    private URI getSearchUri(AsynchronousSearchRestClient client) throws Exception {
        Field field = AsynchronousSearchRestClient.class.getDeclaredField("searchUri");
        field.setAccessible(true);
        return (URI) field.get(client);
    }

    private HttpClient getApacheClient(AbstractAsynchronousRestClient client) throws Exception {
        Field field = AbstractAsynchronousRestClient.class.getDeclaredField("client");
        field.setAccessible(true);
        return (HttpClient) field.get(client);
    }

    public interface Config {
        String getRestrictToSingleCardId();

        boolean getWriteToJira();

        String getJiraUrl();

        String getJiraUsername();

        String getJiraPassword();
    }

    /* qq
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
    }*/
}
