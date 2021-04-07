package com.softwire.todos;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

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
                List<String> output = git("ls-remote", "--get-url", "origin");
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
            return Iterables.getOnlyElement(git("symbolic-ref", "--short", "HEAD"));
        }
    }

    /**
     * Runs the `git` command with the given args in this checkout and returns the output
     */
    public List<String> git(String... args) throws Exception {
        return exec(Lists.asList("git", args).toArray(new String[0]));
    }

    private List<String> exec(String... cmd) throws Exception {
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
