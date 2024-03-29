package com.softwire.todos;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

public class GitCheckout {
    private final File baseDir;
    private final SourceControlLinkFormatter linkFormatter;
    private static final Pattern GITHUB_URL_PAT = Pattern.compile(
            "git@(?<hostname>(github|gitlab)\\.[\\w.-]+):(?<path>.*)\\.git");

    public GitCheckout(File baseDir, Config config) throws Exception {
        this.baseDir = baseDir;
        this.linkFormatter = createLinkFormatter(config);
    }

    public SourceControlLinkFormatter getSourceControlLinkFormatter() {
        return linkFormatter;
    }

    public File getBaseDir() {
        return baseDir;
    }

    private SourceControlLinkFormatter createLinkFormatter(
            Config config)
            throws Exception {

        String gitBranchName = determineGitBranchName();

        if (config.getGitblitUrl() != null) {
            return new SourceControlLinkFormatter.Gitblit(config.getGitblitUrl(), gitBranchName);
        } else if (config.getGithubUrl() != null) {
            return new SourceControlLinkFormatter.Github(config.getGithubUrl(), gitBranchName);
        } else {
            // Auto-detect
            try {
                // (In Git >= 2.7.0 we could do "git remote get-url origin")
                List<String> output = git(asList("ls-remote", "--get-url", "origin"));
                String originUrl = Iterables.getOnlyElement(output);
                Matcher matcher = GITHUB_URL_PAT.matcher(originUrl);
                checkArgument(matcher.matches());
                String githubUrl = String.format(
                        "https://%s/%s",
                        matcher.group("hostname"),
                        matcher.group("path"));
                return new SourceControlLinkFormatter.Github(githubUrl, gitBranchName);
            } catch (Exception e) {
                throw new ConfigException(
                        "Unable to auto-detect a GitHub or GitLab URL for this checkout. " +
                                "Please specify --github-url or --gitblit-url or fix the cause",
                        e);
            }
        }
    }

    public String determineGitBranchName() throws Exception {
        // On Jenkins, $GIT_BRANCH will be e.g. origin/master
        String gitBranchEnv = System.getenv("GIT_BRANCH");
        if (gitBranchEnv != null) {
            Matcher matcher = Pattern.compile("origin/(.*)")
                    .matcher(gitBranchEnv);
            Preconditions.checkState(matcher.matches());
            return matcher.group(1);
        } else {
            return Iterables.getOnlyElement(git(asList("symbolic-ref", "--short", "HEAD")));
        }
    }

    /**
     * Runs the `git` command with the given args in this checkout and returns the output
     */
    public List<String> git(List<String> cmd) throws Exception {
        return git(cmd, singleton(0));
    }

    public List<String> git(List<String> cmd, Set<Integer> expectedReturnCodes) throws Exception {
        List<String> completeCommand = new ArrayList<>();
        completeCommand.add("git");
        completeCommand.addAll(cmd);
        return exec(completeCommand, expectedReturnCodes);
    }

    private List<String> exec(List<String> cmd, Set<Integer> expectedReturnCodes) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(cmd);
        // This simplifies threading, as it avoids deadlock on stderr blocking
        builder.redirectErrorStream(true);

        builder.directory(baseDir);

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
            if (!expectedReturnCodes.contains(ret)) {
                throw new IOException(String.format(
                        "exec \"%s\" failed with code %s. Output was:\n%s",
                        String.join(" ", cmd),
                        ret,
                        String.join("\n", output)));
            }
            return output;
        }
    }

    public interface Config {
        String getGithubUrl();

        String getGitblitUrl();
    }

    private static class ConfigException extends Exception {
        ConfigException(String message, Exception cause) {
            super(message, cause);
        }
    }
}
