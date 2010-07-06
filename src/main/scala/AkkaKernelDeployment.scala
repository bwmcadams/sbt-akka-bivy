/**
* Copyright (c) 2010 Brendan W. McAdams <bwmcadams@evilmonkeylabs.com>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package sbt_akka_bivy

import sbt._

import java.util.jar.Attributes
import java.util.jar.Attributes.Name._
import java.io.File

trait AkkaKernelDeployment extends DefaultProject {
  // Uses some functions swiped from the Akka project's sbt config

  // Config settings you should setup/override in your code
  def manifestProjectName = "Generic Akka Microkernel Deployment"
  def manifestProjectUrl = "http://akkaproject.org"
  def manifestProjectVendor = ""

  /** 
   * JVM Args you want to tack in to the boot script
   */

  def jvmArgs = "-Xms256m -Xmx1g" 
  /**
   * Base setting for the "akka Kernel Class" to run which boots akka.  
   * Defaults to akka kernel, but can be overriden.
   * For example, I have a project which flushes certain caches before booting akka and uses a custom class
   */
  def akkaKernelBootClass = "se.scalablesolutions.akka.kernel.Main"

  /** 
   * Where to find an akka config to work off of. By default we assume it's in 
   * src/main/resources/akka.conf
   */
  def akkaConfig = "src" / "main" / "resources" / "akka.conf"

  /** 
   * Paths to build out local deployments and bundles in
   */
  def bundleDir = "target" / "akka" //Where to build the akka bundle - $PROJECT_DIR/target/akka
  def deployDir = "akka" // Where to deploy locally -  $PROJECT_DIR/akka

  def scalaDeployLibrary = "project" / "boot" / "scala-%s".format(buildScalaVersion) / "lib" / "scala-library.jar"


  def akkaBootScript = """#!/bin/sh
SCALA_VERSION=%s
SCRIPT=$(readlink -f $0)
export AKKA_HOME=`dirname $SCRIPT`
java %s -jar $AKKA_HOME/%s&
echo $!
""".format(buildScalaVersion, jvmArgs, defaultJarPath(".jar").name)

  // ---- STOP OVERRIDING STUFF HERE OR IT WILL ALL GO *BOOM* ----


  //Exclude slf4j1.5.11 from the classpath, it's conflicting...
  override def runClasspath = super.runClasspath --- (super.runClasspath ** "slf4j*1.5.11.jar") 

  /**
   * The "Main" class.
   * We feed the current setting for `akkaKernelBootClass` into this.
   * SBT Needs this to create a runnable jar - leave this be unless you like unexpected behavior
   */
  override def mainClass = Some(akkaKernelBootClass)

  /** 
   * The "bundle-akka" task. This creates the akka bundle for getting it all done.
   * TODO - Ensure akka-kernel is in here somehow ;)
   */
  lazy val akkaBundle = task { 
    log.info("Building Akka Microkernel build in '%s'".format(bundleDir))
    FileUtilities.clean(bundleDir :: Nil, false, log) 
    FileUtilities.createDirectories(bundleDir :: bundleDir / "config" :: bundleDir / "deploy" :: bundleDir :: Nil, log) 
    if (allArtifacts.getFiles.filter(f => f.getName.startsWith("akka-kernel")).size == 0)
        throw new Error("Your dependencies must contain, at the least, a copy of akka-kernel.")

    val dependencies = allArtifacts.getFiles.filter(_.getName.endsWith(".jar"))
    log.debug("Dependencies: %s".format(dependencies.toString))
    FileUtilities.copyFilesFlat(dependencies, bundleDir / "deploy", log) // all dependencies to deploy
    FileUtilities.copyFile(akkaConfig, bundleDir / "config" / "akka.conf", log) // config file
    FileUtilities.copyFile(scalaDeployLibrary.asFile, (bundleDir / "scala-library.jar").asFile, log) // make a local scala-library available 
    log.info("Building and deploying %s to the akka bundle.".format(defaultJarPath(".jar").name))
    FileUtilities.copyFlat(defaultJarPath(".jar") :: Nil, bundleDir, log) // Copy our runtime jar to the root dir
    FileUtilities.copyFlat(defaultJarPath(".jar") :: Nil, bundleDir / "deploy", log) // It also needs to be in deploy for runtime finds esp. for REST via Jersey
    // Create our akka boot script.
    FileUtilities.write((bundleDir / "bootAkka").asFile, akkaBootScript, log)
    None 
  } dependsOn(`package`) describedAs("Bundle an Akka MicroKernel for Deployment")

  /** 
   * The "deploy-akka" task.  Sets up the deploy from the "bundle-akka" in a local deployment directory.
   */
  lazy val akkaDeploy = task {
    log.info("Deploying Akka Microkernel build to '%s'".format(deployDir))
    FileUtilities.clean(deployDir :: Nil, false, log) 
    FileUtilities.copyDirectory(bundleDir, deployDir, log)
    None
  } dependsOn(akkaBundle) describedAs("Deploy, locally, the Akka Microkernel deployment")
  
  lazy val akkaPackage = task {
    log.info("Creating an Akka zip package '%s'.".format(defaultJarPath("-akka.zip")))
    FileUtilities.zip(bundleDir :: Nil, defaultJarPath("-akka.zip"), true, log)
    None
  } dependsOn(akkaBundle) describedAs("Create a packaged ZIP File of the Akka microkernel deployment.")

  /*[>*
   * "publish" actions.
   * Copies, potentially, your akka bundle somewhere else like a dev server.
   <]
  lazy val rsyncAkka = task {
    None
  } dependsOn(`bundleAkka`) describedAs("Push the akka MicroKernel via Rsync to a remote server")
*/
  // Setup the runnable jar.
  override def packageOptions =
    manifestClassPath.map(cp => ManifestAttributes(
      (Attributes.Name.CLASS_PATH, cp),
      (IMPLEMENTATION_TITLE, manifestProjectName),
      (IMPLEMENTATION_URL, manifestProjectUrl),
      (IMPLEMENTATION_VENDOR, manifestProjectVendor)
    )).toList :::
    getMainClass(false).map(MainClass(_)).toList

  // create a manifest with all akka jars and dependency jars on classpath
  override def manifestClassPath = Some(allArtifacts.getFiles
    .filter(_.getName.endsWith(".jar"))
    .filter(!_.getName.contains("servlet_2.4"))
    .filter(!_.getName.contains("scala-library"))
    .map("deploy/" + _.getName)
    .mkString(" ") +
    " scala-library.jar".format(buildScalaVersion)
    )
  // ------------------------------------------------------------
  // helper functions swiped from Akka.
  // ------------------------------------------------------------
  def removeDupEntries(paths: PathFinder) = Path.lazyPathFinder {
     val mapped = paths.get map { p => (p.relativePath, p) }
    (Map() ++ mapped).values.toList
  }

  def allArtifacts = {
    Path.fromFile(buildScalaInstance.libraryJar) +++
    (removeDupEntries(runClasspath filter ClasspathUtilities.isArchive) +++
    ((outputPath ##) / defaultJarName) +++
    mainResources +++
    mainDependencies.scalaJars +++
    descendents(info.projectPath, "*.conf") +++
    descendents(info.projectPath / "scripts", "run_akka.sh") +++
    descendents(info.projectPath / "dist", "*.jar") +++
    descendents(info.projectPath / "deploy", "*.jar") +++
    descendents(path("lib") ##, "*.jar") +++
    descendents(configurationPath(Configurations.Compile) ##, "*.jar"))
    .filter(jar => // remove redundant libs
      !jar.toString.endsWith("stax-api-1.0.1.jar") ||
      !jar.toString.endsWith("scala-library-2.7.7.jar")
    )
  }

  def akkaArtifacts = descendents(info.projectPath / "dist", "*" + buildScalaVersion  + "-" + version + ".jar")


}


// vim: set ts=2 sw=2 sts=2 et:
