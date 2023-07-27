package com.vitorpamplona.amethyst.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            childColumns = ["pkEvent"],
            parentColumns = ["pk"],
            onDelete = CASCADE
        )
    ],
    indices = [
        Index(
            value = ["pkEvent"],
            name = "search_by_pk_event"
        ),
        Index(
            value = ["col0", "col1"],
            name = "search_by_tags_on_person_or_events"
        )
    ]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long? = null,

    var pkEvent: Long? = null,
    val position: Int,

    // Holds 6 columns but can be extended.
    val col0: String?,
    val col1: String?,
    val col2: String?,
    val col3: String?,
    val col4: String?,
    val col5: String?
)
