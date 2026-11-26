package io.github.trunghuynhvan0310.signingkeyvault.data

import io.github.trunghuynhvan0310.signingkeyvault.crypto.BackupKey
import io.github.trunghuynhvan0310.signingkeyvault.crypto.KdfParams
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

data class Project(val id: String, val name: String, val repo: String, val createdAt: Long)

data class KeyItem(
    val id: String,
    val projectId: String,
    val label: String,
    val alias: String,
    val storePassword: String,
    val keyPassword: String,
    val keystoreB64: String,
    val sha1: String,
    val sha256: String,
    val validUntil: Long,
    val createdAt: Long,
)

data class SecretItem(
    val id: String,
    val projectId: String,
    val name: String,
    val value: String,
    val isFile: Boolean,
    val fileName: String,
    val createdAt: Long,
)

/** Khóa sao lưu đã suy ra, lưu trong vault để sao lưu tự động không cần hỏi lại mật khẩu. */
data class BackupSecret(val saltB64: String, val memKb: Int, val iterations: Int, val parallelism: Int, val keyB64: String) {
    fun toKey() = BackupKey(
        Base64.getDecoder().decode(saltB64),
        KdfParams(memKb, iterations, parallelism),
        Base64.getDecoder().decode(keyB64),
    )

    companion object {
        fun from(key: BackupKey) = BackupSecret(
            Base64.getEncoder().encodeToString(key.salt),
            key.kdf.memKb,
            key.kdf.iterations,
            key.kdf.parallelism,
            Base64.getEncoder().encodeToString(key.key),
        )
    }
}

data class VaultData(
    val projects: List<Project> = emptyList(),
    val keys: List<KeyItem> = emptyList(),
    val secrets: List<SecretItem> = emptyList(),
    val backup: BackupSecret? = null,
) {
    /** Gộp dữ liệu khôi phục vào dữ liệu hiện có; mục trùng id giữ bản hiện có. */
    fun mergedWith(other: VaultData): VaultData {
        val projectIds = projects.map { it.id }.toSet()
        val keyIds = keys.map { it.id }.toSet()
        val secretIds = secrets.map { it.id }.toSet()
        return copy(
            projects = projects + other.projects.filter { it.id !in projectIds },
            keys = keys + other.keys.filter { it.id !in keyIds },
            secrets = secrets + other.secrets.filter { it.id !in secretIds },
        )
    }
}

object VaultJson {
    fun encode(data: VaultData, includeBackup: Boolean): ByteArray {
        val root = JSONObject().put("v", 1)
        root.put("projects", JSONArray().also { array ->
            data.projects.forEach {
                array.put(JSONObject().put("id", it.id).put("name", it.name).put("repo", it.repo).put("createdAt", it.createdAt))
            }
        })
        root.put("keys", JSONArray().also { array ->
            data.keys.forEach {
                array.put(
                    JSONObject().put("id", it.id).put("projectId", it.projectId).put("label", it.label)
                        .put("alias", it.alias).put("storePassword", it.storePassword).put("keyPassword", it.keyPassword)
                        .put("keystoreB64", it.keystoreB64).put("sha1", it.sha1).put("sha256", it.sha256)
                        .put("validUntil", it.validUntil).put("createdAt", it.createdAt),
                )
            }
        })
        root.put("secrets", JSONArray().also { array ->
            data.secrets.forEach {
                array.put(
                    JSONObject().put("id", it.id).put("projectId", it.projectId).put("name", it.name)
                        .put("value", it.value).put("isFile", it.isFile).put("fileName", it.fileName)
                        .put("createdAt", it.createdAt),
                )
            }
        })
        val backup = data.backup
        if (includeBackup && backup != null) {
            root.put(
                "backup",
                JSONObject().put("salt", backup.saltB64).put("memKb", backup.memKb)
                    .put("iterations", backup.iterations).put("parallelism", backup.parallelism).put("key", backup.keyB64),
            )
        }
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): VaultData {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        fun <T> list(name: String, read: (JSONObject) -> T): List<T> {
            val array = root.optJSONArray(name) ?: return emptyList()
            return List(array.length()) { read(array.getJSONObject(it)) }
        }
        return VaultData(
            projects = list("projects") {
                Project(it.getString("id"), it.getString("name"), it.optString("repo"), it.optLong("createdAt"))
            },
            keys = list("keys") {
                KeyItem(
                    it.getString("id"), it.getString("projectId"), it.optString("label"), it.getString("alias"),
                    it.getString("storePassword"), it.getString("keyPassword"), it.getString("keystoreB64"),
                    it.optString("sha1"), it.optString("sha256"), it.optLong("validUntil"), it.optLong("createdAt"),
                )
            },
            secrets = list("secrets") {
                SecretItem(
                    it.getString("id"), it.getString("projectId"), it.getString("name"), it.getString("value"),
                    it.optBoolean("isFile"), it.optString("fileName"), it.optLong("createdAt"),
                )
            },
            backup = root.optJSONObject("backup")?.let {
                BackupSecret(it.getString("salt"), it.getInt("memKb"), it.getInt("iterations"), it.getInt("parallelism"), it.getString("key"))
            },
        )
    }
}
