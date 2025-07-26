// Additional repositories for better dependency resolution
resolvers ++= Seq(
  "Maven Central" at "https://repo1.maven.org/maven2/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/",
  "Scala Tools" at "https://oss.sonatype.org/content/groups/scala-tools/"
)