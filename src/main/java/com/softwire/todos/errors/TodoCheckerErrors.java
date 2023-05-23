package com.softwire.todos.errors;

import java.util.ArrayList;
import java.util.List;

public class TodoCheckerErrors {
    private final List<WithoutCardError> withoutCardErrors;
    private final List<WithInvalidStatusError> withInvalidStatusErrors;
    private final List<WithResolvedCardError> withResolvedCardErrors;
    private final List<WithNonExistentCardError> withNonExistentCardErrors;

    public TodoCheckerErrors(
            List<WithoutCardError> withoutCardErrors,
            List<WithInvalidStatusError> withInvalidStatusErrors,
            List<WithResolvedCardError> withResolvedCardErrors,
            List<WithNonExistentCardError> withNonExistentCardErrors
    ) {
        this.withoutCardErrors = withoutCardErrors;
        this.withInvalidStatusErrors = withInvalidStatusErrors;
        this.withResolvedCardErrors = withResolvedCardErrors;
        this.withNonExistentCardErrors = withNonExistentCardErrors;
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

    public List<WithNonExistentCardError> getWithNonExistentCardErrors() {
        return withNonExistentCardErrors;
    }

    public static TodoCheckerErrors empty() {
        return new TodoCheckerErrors(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public boolean isSuccess() {
        return withoutCardErrors.isEmpty()
                && withInvalidStatusErrors.isEmpty()
                && withResolvedCardErrors.isEmpty()
                && withNonExistentCardErrors.isEmpty();
    }
}
