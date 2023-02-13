package com.softwire.todos.jira;

import com.atlassian.jira.rest.client.api.domain.ServerInfo;
import com.softwire.todos.jira.JiraClient;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class JiraClientRetryTest {

    @Test
    public void testThatHttp429IsRetried() throws Exception {
        // Arrange
        HttpServer server = createServerWithTooManyRequestsResponses();
        try {
            JiraClient jiraClient = new JiraClient(new JiraClient.Config() {
                @Override
                public String getRestrictToSingleCardId() {
                    return null;
                }

                @Override
                public boolean getWriteToJira() {
                    return false;
                }

                @Override
                public String getJiraUrl() {
                    return "http://localhost:" + server.getAddress().getPort();
                }

                @Override
                public String getJiraUsername() {
                    return "user";
                }

                @Override
                public String getJiraPassword() {
                    return "password";
                }
            });

            // Act
            ServerInfo serverInfo = jiraClient.getServerInfo();

            // Assert
            assertThat(serverInfo, notNullValue());
        } finally {
            server.stop(5);
        }
    }

    /**
     * A started HTTP server which will return 429 Too Many Requests on the first
     * request and a 200 OK body on the second request.
     */
    private HttpServer createServerWithTooManyRequestsResponses() throws IOException {
        AtomicInteger requestCount = new AtomicInteger(0);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        HttpContext context = server.createContext("/");
        context.setHandler((he) -> {
            switch (requestCount.getAndIncrement()) {
                case 0: {
                    byte[] body = "Retry later".getBytes(StandardCharsets.UTF_8);
                    Headers responseHeaders = he.getResponseHeaders();
                    responseHeaders.add("Retry-After", "2");
                    he.sendResponseHeaders(429, body.length);
                    try (OutputStream out = he.getResponseBody()) {
                        out.write(body);
                    }
                    he.close();
                    break;
                }

                case 1: {
                    byte[] body = ("{\"baseUrl\":\"example.com\"," +
                                   "\"version\":1," +
                                   "\"buildNumber\":1," +
                                   "\"buildDate\":\"2018-01-01T00:00:00.000+0000\"," +
                                   "\"scmInfo\":1," +
                                   "\"serverTitle\":1," +
                                   "}").getBytes();
                    Headers responseHeaders = he.getResponseHeaders();
                    responseHeaders.add("Content-Type", "application/json");
                    he.sendResponseHeaders(200, body.length);
                    try (OutputStream out = he.getResponseBody()) {
                        out.write(body);
                    }
                    he.close();
                    break;
                }

                default:
                    throw new IllegalStateException("More than two requests!");
            }

        });

        server.start();

        return server;
    }
}
