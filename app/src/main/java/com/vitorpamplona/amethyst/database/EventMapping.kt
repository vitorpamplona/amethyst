package com.vitorpamplona.amethyst.database

import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventFactory

class EventMapping(val eventDao: EventDao) {
    fun insert(event: Event) {
        eventDao.insertEventWithTags(event.toEventWithTags())
    }

    fun insert(event: List<Event>) {
        eventDao.insertListOfEventWithTags(event.map { it.toEventWithTags() })
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

    fun getByPTags(pTags: List<String>, kind: Int): List<Event> {
        return eventDao.getByPTagsAndKind(pTags, kind).map {
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
        }
    )
}

fun TagEntity.toTags(): List<String> {
    return listOfNotNull(
        col0Name,
        col1Value,
        col2Differentiator,
        col3Amount
    ).plus(col4Plus)
}

fun Event.toEventWithTags(): EventWithTags {
    val dbEvent = EventEntity(
        id = id,
        pubkey = pubKey,
        createdAt = createdAt,
        kind = kind,
        content = content,
        sig = sig
    )

    val dbTags = tags.mapIndexed { index, tag ->
        TagEntity(
            position = index,
            col0Name = tag.getOrNull(0), // tag name
            col1Value = tag.getOrNull(1), // tag value
            col2Differentiator = tag.getOrNull(2), // marker
            col3Amount = tag.getOrNull(3), // value
            col4Plus = if (tag.size > 4) tag.subList(4, tag.size) else emptyList()
        )
    }

    return EventWithTags(dbEvent, dbTags)
}
