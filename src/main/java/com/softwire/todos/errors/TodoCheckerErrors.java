package com.softwire.todos.errors;

import java.util.ArrayList;
import java.util.List;

public class TodoCheckerErrors {
    private final List<WithoutCardError> withoutCardErrors;
    private final List<WithInvalidStatusError> withInvalidStatusErrors;
    private final List<WithResolvedCardError> withResolvedCardErrors;

    public TodoCheckerErrors(List<WithoutCardError> withoutCardErrors, List<WithInvalidStatusError> withInvalidStatusErrors, List<WithResolvedCardError> withResolvedCardErrors) {
        this.withoutCardErrors = withoutCardErrors;
        this.withInvalidStatusErrors = withInvalidStatusErrors;
        this.withResolvedCardErrors = withResolvedCardErrors;
    }

    public List<WithoutCardError> getWithoutCardErrors() {
        return withoutCardErrors;
    }

    public List<WithInvalidStatusError> getWithInvalidStatusErrors() {
        return withInvalidStatusErrors;
    }

    public List<WithResolvedCardError> getWithResolvedCardErrors() {
        return withResolvedCardErrors;
    }

    public static TodoCheckerErrors empty() {
        return new TodoCheckerErrors(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public boolean isSuccess() {
        return withoutCardErrors.isEmpty() && withInvalidStatusErrors.isEmpty() && withResolvedCardErrors.isEmpty();
    }
}
