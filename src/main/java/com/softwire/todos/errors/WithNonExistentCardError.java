package com.softwire.todos.errors;

import com.softwire.todos.CodeTodo;

import java.util.Collection;

public class WithNonExistentCardError extends TodoCheckerError {
    public WithNonExistentCardError(Collection<CodeTodo> codeTodos) {
        super(codeTodos);
    }
}
