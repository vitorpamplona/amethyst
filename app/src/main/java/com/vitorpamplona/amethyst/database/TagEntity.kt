package com.vitorpamplona.amethyst.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

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
            name = "tags_by_pk_event"
        ),
        Index(
            value = ["col0Name", "col1Value"],
            name = "tags_by_tags_on_person_or_events"
        )
    ]
)

data class TagEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long? = null,

    var pkEvent: Long? = null,
    val position: Int,

    // Holds 6 columns but can be extended.
    val col0Name: String?,
    val col1Value: String?,
    val col2Differentiator: String?,
    val col3Amount: String?,
    val col4Plus: List<String>
)

class Converters {
    val mapper = jacksonObjectMapper()

    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value == null) return emptyList()
        if (value == "") return emptyList()
        return mapper.readValue(value)
    }

    @TypeConverter
    fun fromList(list: List<String?>?): String {
        if (list == null) return ""
        if (list.isEmpty()) return ""
        return mapper.writeValueAsString(list)
    }
}
