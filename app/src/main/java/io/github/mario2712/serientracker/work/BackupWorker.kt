package io.github.mario2712.serientracker.work

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.mario2712.serientracker.SerienTrackerApp
import io.github.mario2712.serientracker.data.export.BackupRepository

/** Weekly archive into the folder the user picked; keeps the newest [AutoBackup.KEEP] files. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SerienTrackerApp).container
        val folder = container.settings.currentBackground().backupFolder ?: return Result.success()
        return try {
            AutoBackup.run(applicationContext, Uri.parse(folder), container.backupRepository)
            container.settings.setBackupResult(System.currentTimeMillis(), null)
            Result.success()
        } catch (e: Exception) {
            container.settings.setBackupResult(null, e.message ?: e::class.simpleName)
            Result.retry()
        }
    }
}

object AutoBackup {
    const val KEEP = 5
    private const val PREFIX = "serientracker_backup_"

    suspend fun run(context: Context, folderUri: Uri, backups: BackupRepository) {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
        check(folder != null && folder.canWrite()) { "Backup-Ordner nicht erreichbar. Bitte in den Einstellungen neu wählen." }
        val name = BackupRepository.fileName()
        // Same day again: replace today's file.
        folder.findFile(name)?.delete()
        val file = checkNotNull(folder.createFile(BackupRepository.MIME_JSON, name)) { "Datei konnte nicht angelegt werden" }
        val out = checkNotNull(context.contentResolver.openOutputStream(file.uri)) { "Datei kann nicht geschrieben werden" }
        out.use { backups.export(it) }
        val existing = folder.listFiles().mapNotNull { it.name }
        filesToDelete(existing).forEach { old -> folder.findFile(old)?.delete() }
    }

    /** Archive files beyond the newest [keep] (names contain the date, so they sort by age). */
    fun filesToDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { it.startsWith(PREFIX) && it.endsWith(".json") }
            .sortedDescending()
            .drop(keep)
}
