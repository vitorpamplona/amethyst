package com.vitorpamplona.amethyst.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index(
            value = ["id"],
            name = "id_is_hash",
            unique = true
        ),
        Index(
            value = ["pubkey", "kind"],
            name = "most_common_search_is_pubkey_kind",
            orders = [Index.Order.ASC, Index.Order.ASC]
        )
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long? = null,
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val content: String,
    val sig: String
)
