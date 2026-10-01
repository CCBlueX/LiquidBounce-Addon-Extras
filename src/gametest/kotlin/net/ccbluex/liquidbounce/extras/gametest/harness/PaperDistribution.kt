package net.ccbluex.liquidbounce.extras.gametest.harness

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name

/**
 * The newest Paper build for the game's version and the newest Grim build for Paper, downloaded once
 * into [cache] and verified against the checksums their APIs publish. Grim's Modrinth listing lags behind
 * new Minecraft versions, so the newest Paper build is taken regardless of its declared game versions.
 */
class PaperDistribution(private val cache: Path) {

    private val logger = LogManager.getLogger("Extras/PaperDistribution")
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    private val gson = Gson()

    fun paper(minecraft: String, build: String?): Path = cached("paper-$minecraft-*.jar") {
        val metadata = get<PaperBuild>(
            "https://fill.papermc.io/v3/projects/paper/versions/$minecraft/builds/${build ?: "latest"}",
        )
        val download = metadata.downloads.getValue("server:default")
        logger.info("Paper {} build {} ({})", minecraft, metadata.id, metadata.channel)
        download(download.url, download.name, "SHA-256", download.checksums.sha256)
    }

    fun grim(version: String?): Path = cached("grimac-bukkit-*.jar") {
        val loaders = URLEncoder.encode("""["paper"]""", StandardCharsets.UTF_8)
        val versions = get<Array<ModrinthVersion>>(
            "https://api.modrinth.com/v2/project/grimac/version?loaders=$loaders",
        )
        val release = versions.firstOrNull { version == null || it.versionNumber == version }
            ?: error("Grim $version is not on Modrinth")
        val file = release.files.first { it.primary }
        logger.info("Grim {}", release.versionNumber)
        download(file.url, file.filename, "SHA-512", file.hashes.sha512)
    }

    /** Falls back to the newest cached download when the APIs cannot be reached. */
    private fun cached(pattern: String, resolve: () -> Path): Path = try {
        resolve()
    } catch (exception: IOException) {
        val fallback = cache.takeIf { it.exists() }
            ?.listDirectoryEntries(pattern)
            ?.maxByOrNull { Files.getLastModifiedTime(it) }
            ?: throw exception
        logger.warn("Offline, using cached {}", fallback.name, exception)
        fallback
    }

    private fun download(url: String, name: String, algorithm: String, checksum: String): Path {
        val target = cache.createDirectories().resolve(name)
        if (target.exists() && target.digest(algorithm) == checksum) {
            return target
        }

        val partial = cache.resolve("$name.part")
        val response = http.send(request(url), HttpResponse.BodyHandlers.ofFile(partial))
        if (response.statusCode() != 200) {
            throw IOException("$url answered ${response.statusCode()}")
        }
        check(partial.digest(algorithm) == checksum) { "$name does not match its published $algorithm checksum" }
        return partial.moveTo(target, overwrite = true)
    }

    private inline fun <reified T> get(url: String): T {
        val response = http.send(request(url), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IOException("$url answered ${response.statusCode()}")
        }
        return gson.fromJson(response.body(), T::class.java)
    }

    private fun request(url: String) = HttpRequest.newBuilder(URI(url)).header("User-Agent", USER_AGENT).build()

    private fun Path.digest(algorithm: String) =
        MessageDigest.getInstance(algorithm).digest(Files.readAllBytes(this)).toHexString()

    @JvmRecord
    private data class PaperBuild(val id: Int, val channel: String, val downloads: Map<String, PaperDownload>)

    @JvmRecord
    private data class PaperDownload(val name: String, val url: String, val checksums: PaperChecksums)

    @JvmRecord
    private data class PaperChecksums(val sha256: String)

    @JvmRecord
    private data class ModrinthVersion(
        @SerializedName("version_number") val versionNumber: String,
        val files: List<ModrinthFile>,
    )

    @JvmRecord
    private data class ModrinthFile(
        val url: String,
        val filename: String,
        val primary: Boolean,
        val hashes: ModrinthHashes,
    )

    @JvmRecord
    private data class ModrinthHashes(val sha512: String)

    private companion object {
        // Both APIs ask for an agent that names the software and a way to reach its authors
        const val USER_AGENT =
            "LiquidBounce-Addon-Extras game tests (https://github.com/CCBlueX/LiquidBounce-Addon-Extras)"
    }

}
