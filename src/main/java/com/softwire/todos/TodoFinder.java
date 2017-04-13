package com.softwire.todos;

import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Verify.verify;
import static java.util.stream.Collectors.toList;

public class TodoFinder {

    private static final Pattern GREP_LINE_PATT = Pattern.compile(
            "([^:]+):(\\d+):(.*)");
    private final File srcDir;

    public TodoFinder(File srcDir) {
        this.srcDir = srcDir;
    }

    public List<CodeTodo> findAllTodosInSource(String excludePathRegex) throws Exception {
        // We use "git grep" since it will automatically search only in committed
        // files without needing any complicated features.
        List<String> matches = git("grep", "-iIwn", "to" + "do");

        Pattern excludePat = excludePathRegex == null ? null : Pattern.compile(excludePathRegex);

        return matches.stream()
                .filter(x -> excludePat == null || !excludePat.matcher(x).find())
                .map(this::gitGrepLineToTodo)
                .collect(toList());
    }

    private CodeTodo gitGrepLineToTodo(String line) {
        Matcher matcher = GREP_LINE_PATT.matcher(line);
        verify(matcher.matches(), "Unexpected `git grep -n` output: '%s'", line);
        return new CodeTodo(
                new File(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                matcher.group(3));
    }

    private List<String> git(String... args) throws Exception {
        return exec(Lists.asList("git", args).toArray(new String[0]));
    }

    private List<String> exec(String... cmd) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(cmd);
        // This simplifies threading, as it avoids deadlock on stderr blocking
        builder.redirectErrorStream(true);

        builder.directory(srcDir);

        Process process = builder.start();

        process.getOutputStream().close();

        ArrayList<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
            int ret = process.waitFor();
            if (ret != 0) {
                throw new IOException(String.format(
                        "exec \"%s\" failed with code %s. Output was:\n%s",
                        String.join(" ", cmd),
                        ret,
                        String.join("\n", output)));
            }
            return output;
        }
    }
}
