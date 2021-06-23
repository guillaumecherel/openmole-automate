import sbtassembly.AssemblyPlugin.defaultUniversalScript


lazy val app = project
  .in(file("."))
  .settings(

    name := "openmole-automate",
    version := "0.1.0",

    // assembly / mainClass := Some("omautom.openmoleAutomate"),
    assembly / assemblyJarName := "openmole-automate",
    assembly / assemblyMergeStrategy := {
      case "module-info.class" => MergeStrategy.concat
      case PathList("org", "reactivestreams", xs @ _*) => MergeStrategy.last
      case x => (assembly / assemblyMergeStrategy).value(x)
    },
    assembly / assemblyPrependShellScript := Some(defaultUniversalScript(shebang = false)),

    scalaVersion := "3.0.0",

    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.12.2",
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
    libraryDependencies += "com.softwaremill.sttp.client3" %% "core" % "3.3.6",
    libraryDependencies += "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.3.6",
    libraryDependencies += "dev.zio" %% "zio" % "1.0.9",
    // libraryDependencies += "dev.zio" %% "zio-streams" % "1.0.9",
    libraryDependencies += "org.apache.commons" % "commons-compress" % "1.20",
    libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.9",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % "test",
    libraryDependencies += ("tech.sparse" %%  "toml-scala" % "0.2.2").cross(CrossVersion.for3Use2_13),


    // Activate ScalaTest reminder on stdout with short stacktrace. See
    // https://www.scalatest.org/user_guide/using_scalatest_with_sbt
    Test/testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oT"),
  )
