package com.softwire.todos;

public class SlackClientException extends Exception {
    public SlackClientException(String message) {
    }

    public SlackClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
