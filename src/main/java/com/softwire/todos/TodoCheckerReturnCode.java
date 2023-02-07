package com.softwire.todos;

public enum TodoCheckerReturnCode {
    SUCCESS(0),
    // We don't need to manually return ERROR as the JVM will return 1 automatically when an uncaught exception is
    // raised.
    @SuppressWarnings("unused") ERROR(1),
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
