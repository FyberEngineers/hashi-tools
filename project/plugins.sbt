logLevel := Level.Warn
resolvers += Resolver.bintrayRepo("kamon-io", "sbt-plugins")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.6")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.9")
addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.11")
addSbtPlugin("io.kamon" % "sbt-aspectj-runner" % "1.1.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")

