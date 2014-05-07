name := "BridgePF"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.4"

resolvers += "Sage Local Repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-releases-local"

libraryDependencies ++= Seq(
  javaJdbc,
  javaEbean,
  cache,
  "com.amazonaws" % "aws-java-sdk" % "1.7.7",
  "org.springframework" % "spring-context" % "4.0.3.RELEASE",
  "org.springframework" % "spring-test" % "4.0.3.RELEASE",
  "org.sagebionetworks" % "synapseJavaClient" % "2014-04-23-1152-e52b875",
  "cglib" % "cglib" % "2.2.2",
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.13",
  "com.google.guava" % "guava" % "17.0",
  "org.mockito" % "mockito-all" % "1.9.5",
  "org.jasypt" % "jasypt" % "1.9.2"
)

play.Project.playJavaSettings
