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

    private const val EXTRA_STUDENT_ID = "notification_student_id"
    private const val EXTRA_CHECK = "notification_check"
    private const val EXTRA_MESSAGE_ID = "notification_message_id"
    private const val EXTRA_IS_HEBE = "notification_is_hebe"
    private const val EXTRA_HEBE_CONTENT = "notification_hebe_content"

    /** Opens the app on the student and screen of [target]; [key] keeps the intents of separate notifications apart. */
    private fun openAppIntent(context: Context, key: String, target: NotificationTarget): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_STUDENT_ID, target.studentId)
            .putExtra(EXTRA_CHECK, target.check.name)
            .putExtra(EXTRA_MESSAGE_ID, target.messageId)
            .putExtra(EXTRA_IS_HEBE, target.isHebe)
            .putExtra(EXTRA_HEBE_CONTENT, target.hebeContent)
        return PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** The target of a tapped notification, or null when [intent] did not come from one. */
    fun targetFrom(intent: Intent?): NotificationTarget? {
        val check = intent?.getStringExtra(EXTRA_CHECK)
            ?.let { name -> BackgroundCheck.entries.firstOrNull { it.name == name } }
            ?: return null
        return NotificationTarget(
            studentId = intent.getStringExtra(EXTRA_STUDENT_ID),
            check = check,
            messageId = intent.getStringExtra(EXTRA_MESSAGE_ID),
            isHebe = intent.getBooleanExtra(EXTRA_IS_HEBE, false),
            hebeContent = intent.getStringExtra(EXTRA_HEBE_CONTENT)
        )
    }

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
            .setContentIntent(openAppIntent(context, update.key, NotificationTarget(update.studentId, update.check)))
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
            .setContentIntent(
                openAppIntent(
                    context,
                    message.key,
                    NotificationTarget(
                        studentId = message.studentId,
                        check = BackgroundCheck.MESSAGES,
                        messageId = message.messageId,
                        isHebe = message.isHebe,
                        hebeContent = message.body.takeIf { message.isHebe }
                    )
                )
            )
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
