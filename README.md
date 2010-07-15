# SBT-Akka-Bivy
## A "BivySack" For Akka - easy microkernel deployment from your SBT project.
### Copyright (c) 2010, Brendan W. McAdams <bwmcadams@evilmonkeylabs.com> Licensed Under Apache 2.0 License

Quick and dirty SBT Plugin for creating Akka Microkernel deployments of your SBT Project.  This creates a proper "akka deploy" setup
with all of your dependencies and configuration files loaded, with a bootable version of your project that you can run cleanly.

This depends on you having a dependency in your project for AT THE LEAST `akka-kernel`.  It tries to check for it and blows up if it doesnt find it.

Usage 
------

You will need to add the plugin to your `project/plugins/Plugins.scala`:

    import sbt._

    class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
          val bumRepo = "Bum Networks Release Repository" at "http://repo.bumnetworks.com/releases"
          val sbtAkkaBivy = "net.evilmonkeylabs" % "sbt-akka-bivy" % "0.2.0"
    }

Then Mix in the AkkaKernelDeployment trait to your Project definition:


    import sbt._ 

    class YourProjectName(info: ProjectInfo) extends DefaultProject(info) with sbt_akka_bivy.AkkaKernelDeployment {

    }

You are now given three new actions within `sbt` ( assuming you reloaded your project ):
    * akka-bundle: Creates an 'akka' directory under 'target' with a full akka deployment and bootable microkernel....
    * akka-deploy: Copies the 'akka' bundle to a local directory where it can be used.
    * akka-package: Creates a zipfile package of the 'akka' bundle for shipping off to wherever you need it.

The 'akka' bundle contains a bootable version of your jar, a directory structure with all your dependencies, and a bootup shell script `bootAkka`. 

Settings
---------
There are a number of defaults you can override.   All of these are `defs` in the `AkkaKernelDeployment` trait, so you should redefine them with `override def ...` in your project file.

By default, the Akka config is created by copying `src/main/resources/akka.conf`.  If you want to change this, set, for example, `override def akkaConfig = "src" / "main" / "resources" / "myProject.akka.conf"` 

The default "Boot" class for Akka is the default one: `se.scalablesolutions.akka.kernel.Main`.  If you'd like to boot something else (For example, in my work project we have a custom file which flushes a few caches and *THEN* boots akka):

    override def akkaKernelBootClass = "net.evilmonkeylabs.my_scala_project.akka.BootClass"

Obviously, your goal is to boot the Akka microkernel here so any non-default class should invoke it.

Finally, you can define the JVM arguments passed to Java:

    override def jvmArgs = "-Xms512m -Xmx2g"

The default is `-Xms256m -Xmx1g`.

Once your directory is setup, just run `bootAkka` and have fun.  I have specifically tested this with the Jersey REST code to ensure it works ( a few prior iterations didn't).

System Level Process Control via Upstart
------------------------------------------
Several distros including Fedora, Ubuntu and Debian support a process control mechanism known as 'upstart'.  As of v 0.2.0 Bivy creates an optional upstart script for you.  You'll find it in your deploy directory as "upstartAkka".

Decide what you want to call your process (e.g. myAkkaSvc) and copy upstartAkka to /etc/init/<serviceName>.conf (e.g. /etc/init/myAkkaSvc.conf).

Edit the file to fill in AKKA_HOME, EXEC_USER and EXEC_GROUP, then run `sudo initctl start <serviceName>`.  Restart currently doesn't work - so you'll need to `stop` and then `start`.


Have fun!
