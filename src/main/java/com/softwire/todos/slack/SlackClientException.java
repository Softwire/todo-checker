package com.softwire.todos.slack;

public class SlackClientException extends Exception {
    public SlackClientException(String message) {
    }

    public SlackClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
