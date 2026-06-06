package com.example.microquest.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.microquest.MainActivity
import com.example.microquest.R
import com.example.microquest.data.AppDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(applicationContext).completedQuestDao()

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000

        val doneToday = dao.completedIds().isNotEmpty()

        showReminder()

        return Result.success()
    }

    private fun showReminder() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Напоминания о квестах",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Ежедневное напоминание выполнить квест" }
        )

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messages = listOf(
            "Твой ежедневный квест ждёт! 🚀",
            "Готов к новому вызову? Questify зовёт! ⚡",
            "Не забудь выполнить квест сегодня! 🏆",
            "Один квест в день — и ты на вершине лидерборда! 🥇"
        )
        val text = messages.random()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Questify")
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "questify_reminder"
        private const val NOTIFICATION_ID = 1001
        private const val WORK_NAME = "questify_daily_reminder"

        fun schedule(context: Context, hour: Int = 10, minute: Int = 0) {
            val delay = calculateInitialDelay(hour, minute)

            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        private fun calculateInitialDelay(hour: Int, minute: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            return target.timeInMillis - now.timeInMillis
        }
    }
}
