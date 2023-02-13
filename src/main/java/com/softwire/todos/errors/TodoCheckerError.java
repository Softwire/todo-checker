package com.softwire.todos.errors;

import com.softwire.todos.CodeTodo;

import java.util.Collection;

public abstract class TodoCheckerError {
    private final Collection<CodeTodo> codeTodos;

    public TodoCheckerError(Collection<CodeTodo> codeTodos) {
        this.codeTodos = codeTodos;
    }

    public Collection<CodeTodo> getCodeTodos() {
        return codeTodos;
    }
}
