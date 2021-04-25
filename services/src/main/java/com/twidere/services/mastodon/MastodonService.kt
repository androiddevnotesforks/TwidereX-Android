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
package com.twidere.services.mastodon

import com.twidere.services.http.AuthorizationInterceptor
import com.twidere.services.http.authorization.BearerAuthorization
import com.twidere.services.http.retrofit
import com.twidere.services.mastodon.api.MastodonResources
import com.twidere.services.mastodon.model.Context
import com.twidere.services.mastodon.model.Emoji
import com.twidere.services.mastodon.model.Hashtag
import com.twidere.services.mastodon.model.MastodonPaging
import com.twidere.services.mastodon.model.MastodonSearchResponse
import com.twidere.services.mastodon.model.NotificationTypes
import com.twidere.services.mastodon.model.Poll
import com.twidere.services.mastodon.model.PostStatus
import com.twidere.services.mastodon.model.PostVote
import com.twidere.services.mastodon.model.SearchType
import com.twidere.services.mastodon.model.UploadResponse
import com.twidere.services.mastodon.model.exceptions.MastodonException
import com.twidere.services.microblog.DownloadMediaService
import com.twidere.services.microblog.LookupService
import com.twidere.services.microblog.MicroBlogService
import com.twidere.services.microblog.NotificationService
import com.twidere.services.microblog.RelationshipService
import com.twidere.services.microblog.SearchService
import com.twidere.services.microblog.StatusService
import com.twidere.services.microblog.TimelineService
import com.twidere.services.microblog.model.INotification
import com.twidere.services.microblog.model.IRelationship
import com.twidere.services.microblog.model.ISearchResponse
import com.twidere.services.microblog.model.IStatus
import com.twidere.services.microblog.model.IUser
import com.twidere.services.microblog.model.Relationship
import com.twidere.services.utils.ProgressListener
import com.twidere.services.utils.ProgressResponseBody
import com.twidere.services.utils.await
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream

class MastodonService(
    private val host: String,
    private val accessToken: String,
) : MicroBlogService,
    TimelineService,
    LookupService,
    RelationshipService,
    NotificationService,
    SearchService,
    StatusService,
    DownloadMediaService {
    private val resources by lazy {
        retrofit<MastodonResources>(
            "https://$host",
            BearerAuthorization(accessToken)
        )
    }

    override suspend fun homeTimeline(
        count: Int,
        since_id: String?,
        max_id: String?
    ) = resources.homeTimeline(max_id, since_id, limit = count)

    override suspend fun mentionsTimeline(
        count: Int,
        since_id: String?,
        max_id: String?
    ): List<IStatus> {
        return resources.notification(
            max_id = max_id,
            since_id = since_id,
            limit = count,
            exclude_types = NotificationTypes.values().filter { it != NotificationTypes.mention }
        )
    }

    override suspend fun userTimeline(
        user_id: String,
        count: Int,
        since_id: String?,
        max_id: String?,
        exclude_replies: Boolean
    ): List<IStatus> = resources.userTimeline(
        user_id = user_id,
        max_id = max_id,
        since_id = since_id,
        limit = count,
        exclude_replies = exclude_replies,
    )

    override suspend fun favorites(
        user_id: String,
        count: Int,
        since_id: String?,
        max_id: String?
    ): List<IStatus> {
        val response = resources.favoritesList(
            max_id = max_id,
            since_id = since_id,
            limit = count,
        )
        return MastodonPaging.from(response)
    }

    override suspend fun lookupUserByName(name: String): IUser {
        TODO("Not yet implemented")
    }

    override suspend fun lookupUsersByName(name: List<String>): List<IUser> {
        TODO("Not yet implemented")
    }

    override suspend fun lookupUser(id: String): IUser {
        return resources.lookupUser(id)
    }

    override suspend fun lookupStatus(id: String): IStatus {
        return resources.lookupStatus(id)
    }

    override suspend fun userPinnedStatus(userId: String): List<IStatus> {
        return resources.userTimeline(user_id = userId, pinned = true)
    }

    override suspend fun showRelationship(target_id: String): IRelationship {
        val response = resources.showFriendships(listOf(target_id)).firstOrNull()
            ?: throw MastodonException("can not fetch relationship")
        return Relationship(
            followedBy = response.following ?: false,
            following = response.followedBy ?: false,
        )
    }

    override suspend fun followers(user_id: String, nextPage: String?) = resources.followers(
        user_id,
        max_id = nextPage,
    ).let {
        MastodonPaging.from(it)
    }

    override suspend fun following(user_id: String, nextPage: String?) = resources.following(
        user_id,
        max_id = nextPage,
    ).let {
        MastodonPaging.from(it)
    }

    override suspend fun follow(user_id: String) {
        resources.follow(user_id)
    }

    override suspend fun unfollow(user_id: String) {
        resources.unfollow(user_id)
    }

    override suspend fun notificationTimeline(
        count: Int,
        since_id: String?,
        max_id: String?
    ): List<INotification> {
        return resources.notification(
            max_id = max_id,
            since_id = since_id,
            limit = count,
        )
    }

    suspend fun context(id: String): Context {
        return resources.context(id)
    }

    suspend fun searchHashTag(
        query: String,
        offset: Int,
        count: Int,
    ): List<Hashtag> {
        return resources.searchV2(
            query = query,
            type = SearchType.hashtags,
            offset = offset,
            limit = count,
        ).hashtags ?: emptyList()
    }

    override suspend fun searchTweets(
        query: String,
        count: Int,
        nextPage: String?
    ): ISearchResponse {
        val result = resources.searchV2(
            query = query,
            type = SearchType.statuses,
            max_id = nextPage,
            limit = count
        )
        return MastodonSearchResponse(
            nextPage = result.statuses?.lastOrNull()?.id,
            status = result.statuses ?: emptyList()
        )
    }

    override suspend fun searchUsers(query: String, page: Int?, count: Int): List<IUser> {
        return resources.searchV2(
            query = query,
            type = SearchType.accounts,
            limit = count,
            offset = (page ?: 0) * count
        ).accounts ?: emptyList()
    }

    suspend fun hashtagTimeline(
        query: String,
        count: Int? = null,
        since_id: String? = null,
        max_id: String? = null,
    ): List<IStatus> = resources.hashtagTimeline(
        hashtag = query,
        limit = count,
        since_id = since_id,
        max_id = max_id,
    )

    override suspend fun like(id: String): IStatus {
        return resources.favourite(id)
    }

    override suspend fun unlike(id: String): IStatus {
        return resources.unfavourite(id).let {
            it.copy(favouritesCount = it.favouritesCount?.let { it - 1 })
        }
    }

    override suspend fun retweet(id: String): IStatus {
        return resources.reblog(id)
    }

    override suspend fun unRetweet(id: String): IStatus {
        return resources.unreblog(id).let {
            it.copy(favouritesCount = it.reblogsCount?.let { it - 1 })
        }
    }

    override suspend fun delete(id: String): IStatus {
        return resources.delete(id)
    }

    suspend fun upload(stream: InputStream, name: String): UploadResponse {
        val body = MultipartBody.Part
            .createFormData("file", name, stream.readBytes().toRequestBody())
        return resources.upload(body)
    }

    suspend fun compose(data: PostStatus): IStatus {
        return resources.post(data)
    }

    suspend fun vote(id: String, choice: List<Int>): Poll {
        return resources.vote(id, PostVote(choices = choice.map { it.toString() }))
    }

    suspend fun emojis(): List<Emoji> = resources.emojis()

    override suspend fun download(target: String, progressListener: ProgressListener): InputStream {
        return OkHttpClient
            .Builder()
            .addInterceptor(AuthorizationInterceptor(BearerAuthorization(accessToken)))
            .addNetworkInterceptor {
                val ori = it.proceed(it.request())
                ori.newBuilder()
                    .body(
                        ori.body?.let { it1 ->
                            ProgressResponseBody(
                                it1,
                                progressListener = progressListener
                            )
                        }
                    )
                    .build()
            }
            .build()
            .newCall(
                Request
                    .Builder()
                    .url(target)
                    .get()
                    .build()
            )
            .await()
            .body
            ?.byteStream() ?: throw IllegalArgumentException()
    }
}
