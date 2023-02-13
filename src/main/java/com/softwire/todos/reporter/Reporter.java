package com.softwire.todos.reporter;

import com.softwire.todos.errors.TodoCheckerErrors;

import java.io.IOException;

public interface Reporter {
    void report(TodoCheckerErrors errors) throws Exception;
}
