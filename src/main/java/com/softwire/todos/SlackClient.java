package com.softwire.todos;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlackClient {
    // The maximum message length of a Slack message is 40K chars
    // https://api.slack.com/methods/chat.postMessage#truncating
    private static final int MAX_MESSAGE_LENGTH = 40_000;
    private static final String TRUNCATION_WARNING =
            "\nThis message was too long for the Slack API and has been truncated.";

    private final Config config;
    private final MethodsClient methodsClient;

    public SlackClient(Config config) {
        this.config = config;
        methodsClient = Slack.getInstance().methods(config.getSlackToken());
    }

    public void postMessage(String message) throws SlackClientException {
        message = truncateLongMessage(message);

        ChatPostMessageRequest request = ChatPostMessageRequest.builder()
                .channel(config.getSlackChannel())
                .text(message)
                .build();

        ChatPostMessageResponse response;
        try {
            response = methodsClient.chatPostMessage(request);
        } catch (Exception e) {
            throw new SlackClientException("Unable to post message to slack", e);
        }
        if (!response.isOk()) {
            throw new SlackClientException("Unable to post message to slack: " + response.getError());
        }
    }

    private static String truncateLongMessage(String message) {
        if (message.length() >= MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH - TRUNCATION_WARNING.length()) + TRUNCATION_WARNING;
        }
        return message;
    }

    public interface Config {
        String getSlackToken();
        String getSlackChannel();
    }
}
