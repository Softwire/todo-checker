# todo-checker

[![Build Status](https://travis-ci.org/Softwire/todo-checker.svg?branch=master)](https://travis-ci.org/Softwire/todo-checker)

This tool checks for TODOs in code and ensures that they are linked to JIRA cards.

## Workflow

This tool is designed to support a workflow where any TODO in the codebase is attached
to an open Jira ticket. Tickets with open TODOs can't be closed, and TODOs without
cards can't be added.

This is one way to ensure that all TODOs get done or are removed in a timely fashion,
avoiding the problem of a codebase littered with many old and forgotten TODO comments.

Setting up a Jenkins or other regular CI build that runs this tool will give your team the
freedom to safely use TODOs to mark pending work directly in the codebase,
safe in the knowledge those TODOs will never get forgotten.

## Functionality

Running his tool will:

1. Update the JIRA cards with a comment listing any code TODOs
which are pending against that card.
2. Exit with a failure status if there are any code TODOs against
cards which are closed or in review, or TODOs with no card at all.
3. Optionally produce a report of all JIRA cards which are in an 
inappropriate state.  This report can be written out to disk or posted
to slack.

## How to use

To run execute:

```
  sbt run \
    --src ../ppc-manager \
    --jira-url https://jira.softwire.com/jira/
    --jira-project AAA \
    --github-url https://github.com/softwire/todo-checker \
    --jira-username Jenkins \
    --jira-password *****
```

This will find pieces of code that look like

```
// TODO: AAA-47 update this code to handle bars as well as foos
```

And link them to the jira ticket AAA-47.

CAUTION: you should not run this project unless you really know what you
are doing; it will make lots of changes to your live JIRA.
The code will not write to JIRA unless you pass `--write-to-jira`; it runs
in a dry-run mode by default.

### Reporting

Pass the `--report-file <file-path>` argument to generate a report file containing 
details of TODO cards which are closed or in review.

Pass the `--slack-token` and `--slack-channel` arguments to post a report to slack
containing details of TODO cards which are closed or in review. 

### Exclusions

Add the string `todo-checker-ignore` to a line to ignore it.

Use the `--exclude-path-regex` argument to exclude files or directories.
