package com.vitorpamplona.amethyst.model

class ParticipantListBuilder {
    private fun addFollowsThatDirectlyParticipateOnToSet(baseNote: Note, followingSet: Set<HexKey>?, set: MutableSet<User>) {
        baseNote.author?.let { author ->
            if (author !in set && (followingSet == null || author.pubkeyHex in followingSet)) set.add(author)
        }

        // Breaks these searchers down to avoid the memory use of creating multiple lists
        baseNote.replies.forEach { reply ->
            reply.author?.let { author ->
                if (author !in set && (followingSet == null || author.pubkeyHex in followingSet)) set.add(author)
            }
        }

        baseNote.boosts.forEach { boost ->
            boost.author?.let { author ->
                if (author !in set && (followingSet == null || author.pubkeyHex in followingSet)) set.add(author)
            }
        }

        baseNote.zaps.forEach { zapPair ->
            zapPair.key.author?.let { author ->
                if (author !in set && (followingSet == null || author.pubkeyHex in followingSet)) set.add(author)
            }
        }

        baseNote.reactions.forEach { reactionSet ->
            reactionSet.value.forEach { reaction ->
                reaction.author?.let { author ->
                    if (author !in set && (followingSet == null || author.pubkeyHex in followingSet)) set.add(author)
                }
            }
        }
    }

    fun followsThatParticipateOnDirect(baseNote: Note?, followingSet: Set<HexKey>?): Set<User> {
        if (baseNote == null) return mutableSetOf()

        val set = mutableSetOf<User>()
        addFollowsThatDirectlyParticipateOnToSet(baseNote, followingSet, set)
        return set
    }

    fun followsThatParticipateOn(baseNote: Note?, followingSet: Set<HexKey>?): Set<User> {
        if (baseNote == null) return mutableSetOf()

        val mySet = mutableSetOf<User>()
        addFollowsThatDirectlyParticipateOnToSet(baseNote, followingSet, mySet)

        baseNote.replies.forEach {
            addFollowsThatDirectlyParticipateOnToSet(it, followingSet, mySet)
        }

        LocalCache.getChannelIfExists(baseNote.idHex)?.notes?.values?.forEach {
            addFollowsThatDirectlyParticipateOnToSet(it, followingSet, mySet)
        }

        return mySet
    }

    fun countFollowsThatParticipateOn(baseNote: Note?, followingSet: Set<HexKey>?): Int {
        if (baseNote == null) return 0

        val list = followsThatParticipateOn(baseNote, followingSet)

        return list.size
    }
}
