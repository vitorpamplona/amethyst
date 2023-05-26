package com.vitorpamplona.amethyst.database

import androidx.room.Embedded
import androidx.room.Relation

data class EventWithTags(
    @Embedded val event: EventEntity,
    @Relation(
        parentColumn = "pk",
        entityColumn = "pkEvent"
    )
    val tags: List<TagEntity>
)
