package com.softwire.todos.errors;

import com.softwire.todos.CodeTodo;

import java.util.Collection;

public class WithoutCardError extends TodoCheckerError {
    public WithoutCardError(Collection<CodeTodo> codeTodos) {
        super(codeTodos);
    }
}
