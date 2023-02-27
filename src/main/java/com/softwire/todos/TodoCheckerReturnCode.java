package com.softwire.todos;

public enum TodoCheckerReturnCode {
    SUCCESS(0),
    ERROR(1),
    INCORRECT_CLI_ARG(2),
    FOUND_INAPPROPRIATE_TODOS(3);

    private final int value;

    TodoCheckerReturnCode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
