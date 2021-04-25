/*
 *  Twidere X
 *
 *  Copyright (C) 2020-2021 Tlaster <tlaster@outlook.com>
 * 
 *  This file is part of Twidere X.
 * 
 *  Twidere X is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  Twidere X is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with Twidere X. If not, see <http://www.gnu.org/licenses/>.
 */
package com.twidere.twiderex.worker

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.twidere.services.microblog.DownloadMediaService
import com.twidere.services.utils.ProgressListener
import com.twidere.twiderex.R
import com.twidere.twiderex.model.MicroBlogKey
import com.twidere.twiderex.notification.NotificationChannelSpec
import com.twidere.twiderex.repository.AccountRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.roundToInt
import kotlin.random.Random

@HiltWorker
class DownloadMediaWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val contentResolver: ContentResolver,
    private val accountRepository: AccountRepository,
    private val notificationManagerCompat: NotificationManagerCompat,
) : CoroutineWorker(context, workerParams) {

    companion object {
        fun create(
            accountKey: MicroBlogKey,
            source: String,
            target: Uri,
        ) = OneTimeWorkRequestBuilder<DownloadMediaWorker>()
            .setInputData(
                Data.Builder()
                    .putString("accountKey", accountKey.toString())
                    .putString("source", source)
                    .putString("target", target.toString())
                    .build()
            )
            .build()
    }

    override suspend fun doWork(): Result {
        val target = inputData.getString("target")?.let { Uri.parse(it) } ?: return Result.failure()
        val source = inputData.getString("source") ?: return Result.failure()
        val accountDetails = inputData.getString("accountKey")?.let {
            MicroBlogKey.valueOf(it)
        }?.let {
            accountRepository.findByAccountKey(accountKey = it)
        }?.let {
            accountRepository.getAccountDetails(it)
        } ?: return Result.failure()
        val service = accountDetails.service
        if (service !is DownloadMediaService) {
            return Result.failure()
        }
        val notificationId = Random.nextInt()
        val file = DocumentFile.fromSingleUri(applicationContext, target)
        val builder = NotificationCompat
            .Builder(applicationContext, NotificationChannelSpec.BackgroundProgresses.id)
            .setContentTitle(applicationContext.getString(R.string.common_alerts_media_saving_title))
            .setSubText(file?.name)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(100, 0, false)
        notificationManagerCompat.notify(notificationId, builder.build())

        try {
            contentResolver.openOutputStream(target)?.use {
                service.download(
                    target = source,
                    progressListener = object : ProgressListener {
                        override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                            builder.setProgress(
                                100,
                                (100f * bytesRead.toFloat() / contentLength.toFloat()).roundToInt(),
                                false
                            )
                            notificationManagerCompat.notify(notificationId, builder.build())
                        }
                    }
                ).copyTo(it)
            } ?: return Result.failure()
            val intent =
                Intent(Intent.ACTION_VIEW, target)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val pendingIntent =
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    intent,
                    PendingIntent.FLAG_MUTABLE
                )
            builder.setOngoing(false)
                .setProgress(0, 0, false)
                .setSilent(false)
                .setAutoCancel(true)
                .setContentTitle(applicationContext.getString(R.string.common_alerts_media_saved_title))
                .setContentIntent(pendingIntent)
            notificationManagerCompat.notify(notificationId, builder.build())
            return Result.success()
        } catch (e: Throwable) {
            builder.setOngoing(false)
                .setProgress(0, 0, false)
                .setSilent(false)
                .setAutoCancel(true)
                .setContentTitle(applicationContext.getString(R.string.common_alerts_media_save_fail_title))
            notificationManagerCompat.notify(notificationId, builder.build())
            return Result.failure()
        }
    }
}
