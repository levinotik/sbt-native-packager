package com.typesafe.sbt
package packager
package universal

import sbt._
import sbt.Keys.{
  cacheDirectory,
  name,
  normalizedName,
  version,
  mappings,
  packageBin,
  packageSrc,
  packageDoc,
  target,
  sourceDirectory,
  streams
}
import packager.Keys._
import Archives._
import sbt.Keys.TaskStreams

/**
 * == Universal Plugin ==
 *
 * Defines behavior to construct a 'universal' zip for installation.
 *
 * == Configuration ==
 *
 * In order to configure this plugin take a look at the available [[com.typesafe.sbt.packager.universal.UniversalKeys]]
 *
 * @example Enable the plugin in the `build.sbt`
 * {{{
 *    enablePlugins(UniversalPlugin)
 * }}}
 */
object UniversalPlugin extends AutoPlugin {

  object autoImport extends UniversalKeys {
    val Universal = config("universal")
    val UniversalDocs = config("universal-docs")
    val UniversalSrc = config("universal-src")

    /**
     * Use native zipping instead of java based zipping
     */
    def useNativeZip: Seq[Setting[_]] =
      makePackageSettings(packageBin, Universal)(makeNativeZip) ++
        makePackageSettings(packageBin, UniversalDocs)(makeNativeZip) ++
        makePackageSettings(packageBin, UniversalSrc)(makeNativeZip)
  }

  import autoImport._

  override def requires = SbtNativePackager
  override def trigger = allRequirements

  /** The basic settings for the various packaging types. */
  override lazy val projectSettings = Seq[Setting[_]](
    // For now, we provide delegates from dist/stage to universal...
    dist <<= dist in Universal,
    stage <<= stage in Universal,
    // TODO - New default to naming, is this right?
    // TODO - We may need to do this for UniversalSrcs + UnviersalDocs
    name in Universal <<= name,
    name in UniversalDocs <<= name in Universal,
    name in UniversalSrc <<= name in Universal,
    packageName in Universal <<= packageName,
    executableScriptName in Universal <<= executableScriptName
  ) ++
    makePackageSettingsForConfig(Universal) ++
    makePackageSettingsForConfig(UniversalDocs) ++
    makePackageSettingsForConfig(UniversalSrc)

  /** Creates all package types for a given configuration */
  private[this] def makePackageSettingsForConfig(config: Configuration): Seq[Setting[_]] =
    makePackageSettings(packageBin, config)(makeZip) ++
      makePackageSettings(packageOsxDmg, config)(makeDmg) ++
      makePackageSettings(packageZipTarball, config)(makeTgz) ++
      makePackageSettings(packageXzTarball, config)(makeTxz) ++
      inConfig(config)(Seq(
        packageName <<= (packageName, version) apply (_ + "-" + _),
        mappings <<= sourceDirectory map findSources,
        dist <<= (packageBin, streams) map printDist,
        stagingDirectory <<= target apply (_ / "stage"),
        stage <<= (streams, stagingDirectory, mappings) map Stager.stage(config.name)
      )) ++ Seq(
        sourceDirectory in config <<= sourceDirectory apply (_ / config.name),
        target in config <<= target apply (_ / config.name)
      )

  private[this] def printDist(dist: File, streams: TaskStreams): File = {
    streams.log.info("")
    streams.log.info("Your package is ready in " + dist.getCanonicalPath)
    streams.log.info("")
    dist
  }

  private type Packager = (File, String, Seq[(File, String)]) => File
  /** Creates packaging settings for a given package key, configuration + archive type. */
  private[this] def makePackageSettings(packageKey: TaskKey[File], config: Configuration)(packager: Packager): Seq[Setting[_]] =
    inConfig(config)(Seq(
      mappings in packageKey <<= mappings map checkMappings,
      packageKey <<= (target, packageName, mappings in packageKey) map packager
    ))

  /** check that all mapped files actually exist */
  private[this] def checkMappings(mappings: Seq[(File, String)]): Seq[(File, String)] = {
    mappings collect { case (f, p) => if (f.exists) (f, p) else sys.error("Mapped file " + f + " does not exist.") }
  }

  /** Finds all sources in a source directory. */
  private[this] def findSources(sourceDir: File): Seq[(File, String)] =
    sourceDir.*** --- sourceDir pair relativeTo(sourceDir)

}
