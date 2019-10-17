import sbt.Keys.scalaVersion

name := "hashi-tools"

version := "0.1"

scalaVersion := "2.12.8"

val scalaV = "2.12.8"

lazy val tools = (project in file("."))
  .settings(
    developers := List(
      Developer(
        id="dani",
        name="Dani Shemesh",
        email="dani.shemesh@hotmail.com",
        url=url("https://fullgc.github.io/")
      ),
      Developer(
        id="Lioz",
        name="Lioz Nudel",
        email="lioz.nudel@fyber.com",
        url=url("https://www.fyber.com/")
      )),
    pgpReadOnly := false,
    name := "secrets",
    organization := "com.fyber",
    organizationName := "Fyber",
    homepage := Some(url("https://github.com/username/projectname")),
    scmInfo := Some(ScmInfo(browseUrl = url("https://github.com/username/projectname"), connection= "git@github.com:username/projectname.git")),
    version := "0.1",
    scalaVersion := scalaV,
    scalacOptions ++= Seq(""),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
      crossScalaVersions := Seq(/*"2.10.7",*/ "2.11.8", "2.12.6"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.5.17",
      "io.kamon" %% "kamon-prometheus" % "1.1.1",
      "com.typesafe" % "config" % "1.3.1",
      "com.bettercloud" % "vault-java-driver" % "3.1.3",
      "com.ecwid.consul" % "consul-api" % "1.3.0",
      "org.scalatest" %% "scalatest" % "3.0.5" % Test,
      "com.amazonaws" % "aws-java-sdk-sts" % "1.11.505",
      "org.springframework" % "spring-core" % "4.1.0.RELEASE",
      "org.reflections" % "reflections" % "0.9.10",
      "io.monix" %% "monix-eval" % "3.0.0-8084549",
      "com.amazonaws" % "aws-java-sdk-iam" % "1.11.505",
      "com.github.andr83" %% "scalaconfig" % "0.6"
    ),
    publishTo := Some(
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging
    ),
    publishMavenStyle := true

  )