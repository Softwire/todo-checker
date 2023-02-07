package com.softwire.todos;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Verify.verify;
import static java.util.stream.Collectors.toList;

public class TodoFinder {

    private static final Pattern GREP_LINE_PATT = Pattern.compile(
            "([^:]+):(\\d+):(.*)");
    private final GitCheckout gitCheckout;

    public TodoFinder(GitCheckout gitCheckout) {
        this.gitCheckout = gitCheckout;
    }

    public List<CodeTodo> findAllTodosInSource(String excludePathRegex) throws Exception {
        // We use "git grep" since it will automatically search only in committed
        // files without needing any complicated features.
        List<String> matches = gitCheckout.git("grep", "-iIwn", "to" + "do");

        Pattern excludePat = excludePathRegex == null ? null : Pattern.compile(excludePathRegex);

        return matches.stream()
                .filter(x -> excludePat == null || !excludePat.matcher(x).find())
                .filter(x -> !x.contains("todo-checker-ignore"))
                .map(this::gitGrepLineToTodo)
                .collect(toList());
    }

    private CodeTodo gitGrepLineToTodo(String line) {
        Matcher matcher = GREP_LINE_PATT.matcher(line);
        verify(matcher.matches(), "Unexpected `git grep -n` output: '%s'", line);
        return new CodeTodo(
                new File(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                matcher.group(3),
                gitCheckout);
    }
}
