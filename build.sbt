import sbtassembly.Plugin.AssemblyKeys._

// put this at the top of the file

assemblySettings

organization := "com.sanoma.cda"

name := "maxmind-geoip2-scala"

version := "1.5.0"

scalaVersion := "2.10.3"

libraryDependencies ++= Seq(
  "com.maxmind.geoip2" % "geoip2" % "2.3.1",
  "com.twitter" %% "util-collection" % "6.23.0",
  "org.scalacheck" %% "scalacheck" % "1.12.2" % "test",
  "org.scalatest"  %% "scalatest"  % "2.2.4" % "test"
)

jarName in assembly := s"${name.value}-${version.value}.jar"

assemblyOption in assembly ~= { _.copy(includeScala = false) }

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
{
  case PathList("org", "apache", xs @ _*) => MergeStrategy.last
  case PathList("org", "objectweb", xs @ _*) => MergeStrategy.last
  case PathList("javax", "servlet", xs @ _*) => MergeStrategy.last
  case PathList("com", "esotericsoftware", xs @ _*) => MergeStrategy.last
  case PathList("com", "google", xs @ _*) => MergeStrategy.last
  case PathList("javax", "xml", xs @ _*) => MergeStrategy.last
  case PathList("com", "twitter", xs @ _*) => MergeStrategy.last
  case PathList("project.clj") => MergeStrategy.last
  case PathList("overview.html") => MergeStrategy.last
  case PathList("logback.xml") => MergeStrategy.discard
  case x => old(x)
}
}

