package com.vitorpamplona.amethyst.database

import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.EventFactory

class EventMapping(val eventDao: EventDao) {
    fun insert(event: Event) {
        val dbEvent = EventEntity(
            id = event.id,
            pubkey = event.pubKey,
            createdAt = event.createdAt,
            kind = event.kind,
            content = event.content,
            sig = event.sig
        )

        val dbTags = event.tags.mapIndexed { index, tag ->
            TagEntity(
                position = index,
                col0 = tag.getOrNull(0),
                col1 = tag.getOrNull(1),
                col2 = tag.getOrNull(2),
                col3 = tag.getOrNull(3),
                col4 = tag.getOrNull(4),
                col5 = tag.getOrNull(5)
            )
        }

        eventDao.insertEventWithTags(dbEvent, dbTags)
    }

    fun get(id: String): Event? {
        return eventDao.getById(id)?.toEvent()
    }

    fun get(ids: List<String>): List<Event> {
        return eventDao.getByIds(ids).map {
            it.toEvent()
        }
    }

    fun getAll(): List<Event> {
        return eventDao.getAll().map {
            it.toEvent()
        }
    }

    fun get(authors: List<String>, kind: Int): List<Event> {
        return eventDao.getByAuthorsAndKind(authors, kind).map {
            it.toEvent()
        }
    }
}

fun EventWithTags.toEvent(): Event {
    return EventFactory.create(
        id = event.id,
        pubKey = event.pubkey,
        createdAt = event.createdAt,
        kind = event.kind,
        content = event.content,
        sig = event.sig,
        tags = tags.map {
            it.toTags()
        },
        lenient = true
    )
}

fun TagEntity.toTags(): List<String> {
    return listOfNotNull(
        col0,
        col1,
        col2,
        col3,
        col4,
        col5
    )
}
