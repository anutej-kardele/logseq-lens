package com.anutej.logseqpocket.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anutej.logseqpocket.LogseqPocketApp
import com.anutej.logseqpocket.data.importer.ImportOutcome
import com.anutej.logseqpocket.data.json.ImportError
import java.util.concurrent.TimeUnit

/**
 * Optional background check (off by default). Android decides the exact timing; the worker only
 * runs on an unmetered network with a healthy battery and does nothing without a saved token.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LogseqPocketApp).container
        if (container.tokens.load() == null) return Result.success()
        return when (val r = container.githubSync.sync()) {
            is ImportOutcome.Failed -> if (r.error is ImportError.Network) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "github-sync"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(NAME); return }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
