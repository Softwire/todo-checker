libraryDependencies ++= Seq(
  "ch.qos.logback"          % "logback-classic"  % "1.1.3",
  "com.atlassian.jira"      % "jira-rest-java-client" % "1.2-m01",
  "args4j"                  % "args4j" % "2.33",
  "com.google.guava"        % "guava" % "19.0")

resolvers ++= Seq(
  "atlassian-public" at "https://maven.atlassian.com/content/repositories/atlassian-public"
)
