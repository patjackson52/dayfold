package works.tether.client

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

// Desktop/JVM store — a single JSON file locked to 0600 (owner read/write).
// Path is a ctor arg so the entrypoint injects ~/.<app>/session.json and tests
// use a temp file. Mirrors dayfold's FileTokenStore.
class FileTokenStore(
  private val file: File,
  private val json: Json = Json { ignoreUnknownKeys = true },
) : TokenStore {

  override fun load(): Session? {
    if (!file.exists()) return null
    return runCatching { json.decodeFromString(Session.serializer(), file.readText()) }.getOrNull()
  }

  override fun save(session: Session) {
    file.parentFile?.mkdirs()
    file.writeText(json.encodeToString(Session.serializer(), session))
    runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
  }

  override fun clear() {
    file.delete()
  }
}
