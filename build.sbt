libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.atlassian.jira" % "jira-rest-java-client" % "1.2-m01",
  "args4j" % "args4j" % "2.33",
  "com.google.guava" % "guava" % "19.0",
  "junit" % "junit" % "4.4" % Test,
  "com.novocode" % "junit-interface" % "0.11" % Test exclude("junit", "junit-dep")
)

resolvers ++= Seq(
  "atlassian-public" at "https://maven.atlassian.com/content/repositories/atlassian-public"
)
