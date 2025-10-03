javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

libraryDependencies ++= {

  Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.3",

    // You may be tempted to update this client, as Atlassian have published versions
    // much newer than 5. However:
    //  = The Atlassian client this is forked from is for the non-Cloud version of Jira
    //  = The client this is forked from does not support comment updating, which we need
    //    and which we added to the fork
    // Despite the age, it's going to be easier to update our fork.
    // This does mean we'll need to stick to old version of Java. qq
    // The main alternative would be to start again with an OpenAPI client,
    // as per https://community.atlassian.com/forums/Jira-articles/Generating-a-REST-client-for-Jira-Cloud/ba-p/1307133
    "com.atlassian.jira" % "jira-rest-java-client-app" % "5.2.2025-rtb"
      notTransitive()
      from "https://richardbradley.github.io/jira-rest-java-client/releases/jira-rest-java-client-app-5.2.2025-rtb-jar-with-dependencies.jar",
    "com.slack.api" % "slack-api-client" % "1.27.3",
    "args4j" % "args4j" % "2.33",
    "junit" % "junit" % "4.4" % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test exclude("junit", "junit-dep")
  )
}

resolvers ++= Seq(
  "richardbradley-bintray" at "https://dl.bintray.com/richardbradley/jira-rest-java-client",
  "atlassian-public" at "https://maven.atlassian.com/content/repositories/atlassian-public",
  "atlassian-public2" at "https://packages.atlassian.com/maven-public/",
  "atlassian-public3" at "https://packages.atlassian.com/public/",
  "atlassian-public4" at "https://m2proxy.atlassian.com/repository/public",
  "spring-plugins" at "https://repo.spring.io/plugins-release/"
)

// Pass through the app's exit code when using `sbt run`
trapExit := false
