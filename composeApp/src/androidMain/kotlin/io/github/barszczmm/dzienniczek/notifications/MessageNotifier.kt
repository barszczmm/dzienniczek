package io.github.barszczmm.dzienniczek.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.Html
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.barszczmm.dzienniczek.MainActivity
import io.github.barszczmm.dzienniczek.R
import io.github.barszczmm.dzienniczek.settings.BackgroundCheck

object MessageNotifier {
    private const val CHANNEL_ID = "messages"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Nowe wiadomości", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pełna treść nowych wiadomości z Librusa i eduVulcan"
            }
        )
        // One channel per kind, so each can be muted separately in the system settings.
        BackgroundCheck.entries.filter { it != BackgroundCheck.MESSAGES }.forEach { check ->
            manager.createNotificationChannel(
                NotificationChannel(channelId(check), check.label, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Nowe wpisy w dzienniku: ${check.label.lowercase()}"
                }
            )
        }
    }

    private fun channelId(check: BackgroundCheck): String = "journal_${check.name.lowercase()}"

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** New grade, exam, homework, note, announcement or attendance entry. */
    fun show(context: Context, update: JournalUpdate) {
        if (!canNotify(context)) return
        createChannel(context)

        val text = toPlainText(update.text, isHtml = false)
        val notification = NotificationCompat.Builder(context, channelId(update.check))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(update.title)
            .setContentText(text.lineSequence().firstOrNull { it.isNotBlank() } ?: "")
            .setSubText(update.studentName)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(update.title).bigText(text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(update.key.hashCode(), notification)
    }

    fun show(context: Context, message: NewMessage) {
        if (!canNotify(context)) return
        createChannel(context)

        val body = toPlainText(message.body, message.isHtml)
        val header = buildString {
            append(message.source)
            message.studentName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.subject.ifBlank { "(bez tematu)" })
            .setContentText("${message.sender}: ${body.lineSequence().firstOrNull { it.isNotBlank() } ?: ""}")
            .setSubText(header)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(message.subject.ifBlank { "(bez tematu)" })
                    .bigText("Od: ${message.sender}\n\n$body")
            )
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .build()

        NotificationManagerCompat.from(context).notify(message.key.hashCode(), notification)
    }

    private fun toPlainText(text: String, isHtml: Boolean): String {
        val looksLikeHtml = isHtml || Regex("<[a-zA-Z/][^>]*>").containsMatchIn(text)
        val plain = if (looksLikeHtml) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString()
        } else {
            text.replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
        }
        return plain.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
