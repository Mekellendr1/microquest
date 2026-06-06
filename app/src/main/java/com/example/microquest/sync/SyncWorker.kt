package com.example.microquest.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.microquest.data.AppDatabase
import com.example.microquest.network.ApiClient
import com.example.microquest.network.SyncQuestRequest
import java.util.concurrent.TimeUnit

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.pendingSyncDao()
        val api = ApiClient.get(applicationContext)

        val pending = dao.getAll()
        if (pending.isEmpty()) return Result.success()

        Log.d("SyncWorker", "Syncing ${pending.size} pending quests…")

        var allOk = true
        for (item in pending) {
            try {
                api.syncQuest(
                    SyncQuestRequest(
                        questId = item.questId,
                        questText = item.questText,
                        questType = item.questType,
                        completedAt = item.completedAt,
                        proofText = item.proofText,
                        mediaUrl = item.mediaUrl
                    )
                )
                dao.deleteById(item.id)
                Log.d("SyncWorker", "Quest ${item.questId} synced and removed from queue")
            } catch (e: Exception) {
                Log.w("SyncWorker", "Failed to sync quest ${item.questId}: ${e.message}")
                allOk = false
            }
        }

        return if (allOk) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "questify_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
