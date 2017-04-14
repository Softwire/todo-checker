# todo-checker

This tool checks for TODOs and matches them to JIRA cards.

Based entirely on Rich B's various todo-checkers from various different projects.

* It will update the JIRA cards with a comment listing any code TODOs
which are pending against that card.

* It will exit with a failure status if there are any code TODOs against
cards which are closed or in review.

To run execute:

```
  sbt run \
    --src ../ppc-manager \
    --jira-project-key TWPPC \
    --github-url https://github.com/treatwell/ppc-manager \
    --jira-project-key-in-todo-regex twppc \
    --jira-username Jenkins \
    --jira-password *****
```

CAUTION: you should not run this project unless you really know what you
are doing; it will make lots of changes to the live JIRA.
