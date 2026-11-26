package io.github.trunghuynhvan0310.signingkeyvault.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.trunghuynhvan0310.signingkeyvault.crypto.BackupFile
import io.github.trunghuynhvan0310.signingkeyvault.crypto.BackupKey
import java.io.IOException

/** Ghi và kiểm tra bản sao lưu trong thư mục do người dùng chọn (Storage Access Framework). */
class BackupManager(context: Context) {
    private val resolver = context.contentResolver

    private class Child(val uri: Uri, val lastModified: Long)

    fun folderName(tree: Uri): String? {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun children(tree: Uri): Map<String, Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val result = HashMap<String, Child>()
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        resolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getString(1)] = Child(
                    DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)),
                    cursor.getLong(2),
                )
            }
        }
        return result
    }

    /**
     * Ghi luân phiên hai tệp A/B (ghi vào tệp cũ hơn) để một lần ghi hỏng không làm mất cả hai,
     * rồi đọc lại và giải mã để chắc chắn tệp dùng được. Trả về tên tệp đã ghi.
     */
    fun backupToTree(tree: Uri, bytes: ByteArray, key: BackupKey): String {
        val existing = children(tree)
        val name = NAMES.firstOrNull { it !in existing }
            ?: NAMES.minByOrNull { existing.getValue(it).lastModified }!!
        val target = existing[name]?.uri
            ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), MIME, name)
            ?: throw IOException("Cannot create file")
        resolver.openOutputStream(target, "wt")?.use { it.write(bytes); it.flush() }
            ?: throw IOException("Cannot open file")
        BackupFile.openWithKey(read(target), key)
        return name
    }

    /** Trả về (số tệp đọc được, số tệp tìm thấy). */
    fun verify(tree: Uri, key: BackupKey): Pair<Int, Int> {
        val existing = children(tree)
        val found = NAMES.filter { it in existing }
        val readable = found.count { runCatching { BackupFile.openWithKey(read(existing.getValue(it).uri), key) }.isSuccess }
        return readable to found.size
    }

    fun write(target: Uri, bytes: ByteArray) {
        resolver.openOutputStream(target, "wt")?.use { it.write(bytes); it.flush() }
            ?: throw IOException("Cannot open file")
    }

    fun read(uri: Uri): ByteArray =
        resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Cannot read file")

    companion object {
        const val MIME = "application/octet-stream"
        val NAMES = listOf("signing-key-vault-A.vault", "signing-key-vault-B.vault")
    }
}
