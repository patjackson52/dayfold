package com.sloopworks.dayfold.cli

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ADR 0037 — easy update path for the dayfold CLI (extends ADR 0031's Homebrew
// distribution). `dayfold update` upgrades a brew-managed install and otherwise
// points at the release; a throttled, fail-silent "update available" nudge runs
// once/day after an interactive push/pull. Rolling `cli-edge` builds are dev
// builds — never nagged and never auto-"updated" to a lower stable.

private val UJSON = Json { ignoreUnknownKeys = true }
private val SEMVER = Regex("""\d+\.\d+\.\d+""")
private const val RELEASES_LATEST = "https://api.github.com/repos/SloopWorks/dayfold/releases/latest"
private const val RELEASES_PAGE = "https://github.com/SloopWorks/dayfold/releases"

/** Latest STABLE CLI semver from GitHub Releases (the API's `latest` excludes
 *  prereleases, so the rolling `cli-edge` pre-release is ignored). null on any
 *  network/parse failure (fail-silent) or a non-`cli-v<semver>` tag. */
internal fun latestStableVersion(): String? = runCatching {
  val req = HttpRequest.newBuilder(URI.create(RELEASES_LATEST))
    .header("accept", "application/vnd.github+json")
    .timeout(Duration.ofSeconds(4))
    .GET().build()
  val res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString())
  if (res.statusCode() != 200) return null
  val tag = UJSON.parseToJsonElement(res.body()).jsonObject["tag_name"]?.jsonPrimitive?.content ?: return null
  tag.removePrefix("cli-v").takeIf { SEMVER.matches(it) }
}.getOrNull()

/** True iff [current] is a clean semver strictly older than [latest]. A non-semver
 *  current (dev/edge/unknown) returns false — those are never "behind". */
internal fun isOlder(current: String, latest: String): Boolean {
  if (!SEMVER.matches(current) || !SEMVER.matches(latest)) return false
  val c = current.split("."); val l = latest.split(".")
  for (i in 0..2) { if (c[i].toInt() != l[i].toInt()) return c[i].toInt() < l[i].toInt() }
  return false
}

/** Heuristic: is this CLI running from a Homebrew keg/prefix? */
internal fun isBrewManaged(): Boolean = runCatching {
  val loc = File(object {}.javaClass.protectionDomain.codeSource.location.toURI()).absolutePath
  loc.contains("/Cellar/dayfold/") || loc.contains("/homebrew/") || loc.contains("/linuxbrew/")
}.getOrDefault(false)

private fun hasBrew(): Boolean = runCatching {
  ProcessBuilder("brew", "--version").redirectErrorStream(true).start().waitFor() == 0
}.getOrDefault(false)

/** `dayfold update` — bring the CLI up to the latest stable. */
fun runUpdate() {
  val current = cliVersion()
  val latest = latestStableVersion()
  when {
    latest == null ->
      System.err.println("couldn't reach GitHub to check for updates — see $RELEASES_PAGE")
    isOlder(current, latest) ->
      println("update available: $current → $latest")
    else -> {
      println(if (SEMVER.matches(current)) "dayfold $current is up to date (latest stable: $latest)"
              else "dayfold $current is a dev/edge build (latest stable: $latest)")
      return
    }
  }
  // The ADR 0031 distribution is Homebrew. Auto-run `brew upgrade` ONLY when this
  // CLI is actually brew-managed (don't run it against a standalone install).
  if (isBrewManaged()) {
    println("running: brew upgrade dayfold")
    val code = runCatching { ProcessBuilder("brew", "upgrade", "dayfold").inheritIO().start().waitFor() }.getOrDefault(1)
    exitProcess(code)
  }
  println("update with Homebrew (the dayfold distribution):")
  println("  brew install sloopworks/tap/dayfold   (first time, once the tap is live)")
  println("  brew upgrade dayfold                   (thereafter)")
  if (!hasBrew()) println("  install Homebrew first: https://brew.sh")
  println("  or grab the latest tarball from $RELEASES_PAGE")
}

/**
 * Opportunistic, throttled (once/day), fail-silent "update available" nudge — call
 * after a successful interactive command. Skips dev/edge builds, non-TTY shells, CI,
 * and `DAYFOLD_NO_UPDATE_CHECK`. Writes the timestamp BEFORE the network call so a
 * slow/failed check never re-fires that day.
 */
fun maybeNudgeUpdate() {
  runCatching {
    val current = cliVersion()
    if (!SEMVER.matches(current)) return                       // dev/edge → never nag
    if (System.getenv("CI") != null) return
    if (System.getenv("DAYFOLD_NO_UPDATE_CHECK") != null) return
    if (System.console() == null) return                       // piped/non-interactive → silent
    val dir = Paths.get(System.getProperty("user.home"), ".dayfold")
    Files.createDirectories(dir)
    val stamp = dir.resolve("update-check")
    val now = System.currentTimeMillis()
    val last = runCatching { Files.readString(stamp).trim().toLong() }.getOrDefault(0L)
    if (now - last < 24L * 60 * 60 * 1000) return              // throttle: once / 24h
    Files.writeString(stamp, now.toString())
    val latest = latestStableVersion() ?: return
    if (isOlder(current, latest))
      System.err.println("→ dayfold $latest is available (you have $current). Run `dayfold update`.")
  }
}
