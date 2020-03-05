libraryDependencies ++= {

  // https://github.com/sbt/sbt/issues/3618
  sys.props += "packaging.type" -> "jar"

  Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.3",

    "com.atlassian.jira" % "jira-rest-java-client-app" % "5.2.2-rtb"
      from "https://richardbradley.github.io/jira-rest-java-client/releases/jira-rest-java-client-app-5.2.2-rtb-jar-with-dependencies.jar",

    // Something weird is going on with Ivy here.
    "com.atlassian.sal" % "sal-api" % "3.0.7"
      from "https://repo.spring.io/plugins-release/com/atlassian/sal/sal-api/3.0.7/sal-api-3.0.7.jar",
    "javax.ws.rs" % "javax.ws.rs-api" % "2.1.1",
    "org.glassfish.jersey.core" % "jersey-common" % "2.30.1",


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
