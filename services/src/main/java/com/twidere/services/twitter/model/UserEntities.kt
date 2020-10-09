package com.twidere.services.twitter.model

import kotlinx.serialization.Serializable

@Serializable
data class UserEntities (
    val url: FluffyURL? = null,
    val description: Description? = null,
)