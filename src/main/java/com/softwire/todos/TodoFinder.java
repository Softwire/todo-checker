package com.softwire.todos;

import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
        // git grep will return 0 if any matching lines found, 1 if no matching lines were found, and
        // 2 otherwise, see https://www.gnu.org/software/grep/manual/html_node/Exit-Status.html.
        List<String> matches = gitCheckout.git(List.of("grep", "-iIwn", "todo"), Set.of(0, 1));  // todo-checker-ignore

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
