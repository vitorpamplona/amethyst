/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip01Core.jackson

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable
import com.vitorpamplona.quartz.nip01Core.core.RawJson
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MessageDeserializer
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MessageSerializer
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CommandDeserializer
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CommandSerializer
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterDeserializer
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterSerializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplateDeserializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplateSerializer
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.jackson.BunkerMessageDeserializer
import com.vitorpamplona.quartz.nip46RemoteSigner.jackson.BunkerRequestDeserializer
import com.vitorpamplona.quartz.nip46RemoteSigner.jackson.BunkerRequestSerializer
import com.vitorpamplona.quartz.nip46RemoteSigner.jackson.BunkerResponseDeserializer
import com.vitorpamplona.quartz.nip46RemoteSigner.jackson.BunkerResponseSerializer
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.jackson.RumorDeserializer
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.jackson.RumorSerializer
import java.io.InputStream
import kotlin.reflect.KClass

class JacksonMapper {
    companion object {
        val defaultPrettyPrinter = InliningTagArrayPrettyPrinter()

        val mapper =
            ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, false)
                .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, false)
                // Tolerate a single JSON object where a list is declared. Several Nostr-native
                // RPCs (e.g. CLINK Manage `details`, typed `OfferData | OfferData[]`) return a
                // bare object for single-item results and an array for lists; accept both.
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .setDefaultPrettyPrinter(defaultPrettyPrinter)
                .registerModule(
                    SimpleModule()
                        // nip 01
                        .addSerializer(RawJson::class.java, RawJsonSerializer())
                        .addSerializer(Event::class.java, EventSerializer())
                        .addDeserializer(Event::class.java, EventDeserializer())
                        .addSerializer(Filter::class.java, FilterSerializer())
                        .addDeserializer(Filter::class.java, FilterDeserializer())
                        .addSerializer(Message::class.java, MessageSerializer())
                        .addDeserializer(Message::class.java, MessageDeserializer())
                        .addSerializer(Command::class.java, CommandSerializer())
                        .addDeserializer(Command::class.java, CommandDeserializer())
                        .addDeserializer(TagArray::class.java, TagArrayDeserializer())
                        .addSerializer(TagArray::class.java, TagArraySerializer())
                        .addDeserializer(EventTemplate::class.java, EventTemplateDeserializer())
                        .addSerializer(EventTemplate::class.java, EventTemplateSerializer())
                        // nip 59
                        .addSerializer(Rumor::class.java, RumorSerializer())
                        .addDeserializer(Rumor::class.java, RumorDeserializer())
                        // nip 46
                        .addDeserializer(BunkerMessage::class.java, BunkerMessageDeserializer())
                        .addSerializer(BunkerRequest::class.java, BunkerRequestSerializer())
                        .addDeserializer(BunkerRequest::class.java, BunkerRequestDeserializer())
                        .addSerializer(BunkerResponse::class.java, BunkerResponseSerializer())
                        .addDeserializer(BunkerResponse::class.java, BunkerResponseDeserializer()),
                )

        /**
         * Shortcuts
         *
         * Built from Class objects, NOT from a TypeReference. A TypeReference
         * reads its type argument back off the anonymous subclass's generic
         * superclass, and R8 in full mode does not keep that -- not with
         * `-keepattributes Signature`, and not with a `-keep` on the subclasses
         * either (both were tried on device). It failed here, in <clinit>, with
         *
         *   IllegalArgumentException: Internal error: TypeReference constructed
         *   without actual type information
         *
         * and a failed <clinit> is permanent: every later touch of this class
         * throws NoClassDefFoundError, so the release build could not hash or sign
         * a single event. TypeFactory takes the Class objects directly, so there is
         * nothing for R8 to erase.
         */
        val eventTypeInstance: JavaType = mapper.typeFactory.constructType(Event::class.java)
        val tagArrayTypeInstance: JavaType = mapper.typeFactory.constructType(Array<Array<String>>::class.java)
        val rumorTypeInstance: JavaType = mapper.typeFactory.constructType(Rumor::class.java)
        val eventTemplateTypeInstance: JavaType = mapper.typeFactory.constructParametricType(EventTemplate::class.java, Event::class.java)
        val eventListTypeInstance: JavaType = mapper.typeFactory.constructCollectionType(List::class.java, Event::class.java)
        val messageTypeInstance: JavaType = mapper.typeFactory.constructType(Message::class.java)
        val commandTypeInstance: JavaType = mapper.typeFactory.constructType(Command::class.java)

        fun fromJson(json: String): Event = mapper.readValue(json, eventTypeInstance)

        fun fromJsonToMessage(json: String): Message = mapper.readValue(json, messageTypeInstance)

        fun fromJsonToCommand(json: String): Command = mapper.readValue(json, commandTypeInstance)

        fun fromJsonToTagArray(json: String): TagArray = mapper.readValue(json, tagArrayTypeInstance)

        fun fromJsonToRumor(json: String): Rumor = mapper.readValue(json, rumorTypeInstance)

        fun fromJsonToEventTemplate(json: String): EventTemplate<Event> = mapper.readValue(json, eventTemplateTypeInstance)

        fun fromJsonToEventList(json: String): List<Event> = mapper.readValue(json, eventListTypeInstance)

        /**
         * The types this mapper has a registered deserializer for, by EXACT class.
         *
         * Exact is right here: `SimpleModule.addDeserializer(Event::class)` binds that
         * class alone — Jackson's SimpleDeserializers does not walk up a hierarchy on
         * the read side — so a subclass passed to [fromJsonTo] really would be
         * unbound. The serializer side is the mirror image; see [checkSerializable].
         *
         * [fromJsonTo] is generic, so nothing in the type system stops a new
         * [OptimizedSerializable] being passed to it. Jackson would answer by binding
         * that type REFLECTIVELY off its Kotlin constructor parameter names — which
         * works in debug, and in a release build writes obfuscated one-letter JSON keys
         * onto the wire. That is how NIP-47 and CLINK ended up needing a package keep
         * rule each, and the symptom only ever shows up in production.
         *
         * So the fallback is closed: an unregistered type fails here, loudly, on the
         * first call. Register a StdSerializer/StdDeserializer pair below, or route the
         * type at kotlinx in OptimizedJsonMapper the way NIP-47 and CLINK are.
         */
        @PublishedApi
        internal val registered: Set<KClass<*>> =
            setOf(
                Event::class,
                Filter::class,
                Message::class,
                Command::class,
                TagArray::class,
                EventTemplate::class,
                Rumor::class,
                BunkerMessage::class,
                BunkerRequest::class,
                BunkerResponse::class,
            )

        @PublishedApi
        internal fun checkRegistered(type: KClass<*>) {
            if (type !in registered) {
                throw IllegalArgumentException(
                    "No Jackson deserializer is registered for $type, so Jackson would bind it " +
                        "reflectively and emit obfuscated field names in a release build. Register " +
                        "one in JacksonMapper, or route the type at KotlinSerializationMapper in " +
                        "OptimizedJsonMapper.",
                )
            }
        }

        /**
         * The write-side twin of [checkRegistered], and the reason it is an `is` chain
         * rather than a set: Jackson's SimpleSerializers DOES walk the hierarchy, so
         * the serializer registered for [Event] serves every event kind and [Command]'s
         * serves NegMsgMessage. Comparing exact classes here would reject all of them.
         *
         * Without this, [toJson] has the same hole [fromJsonTo] had. Hand it an
         * [OptimizedSerializable] with no registered serializer and Jackson falls back
         * to bean introspection, naming each field after its getter — names R8 renames,
         * so a release build writes `{"a":…}` onto the wire while the debug build looks
         * perfect.
         *
         * [BunkerRequest] and [BunkerResponse] are named instead of their [BunkerMessage]
         * parent on purpose: the parent has no serializer of its own, so a third subclass
         * should fail here rather than quietly bean-serialize.
         *
         * Two OptimizedSerializables are deliberately absent, and both would throw if
         * they ever arrived here: CLINK's SatRange, which is only ever written as a
         * field of an Offer/Debit/Manage message by that message's kotlinx serializer,
         * and NIP-55's IntentResult, which goes through JsonMapperNip55 and its own
         * registered serializer. Neither reaches this mapper today.
         */
        private fun checkSerializable(value: OptimizedSerializable) {
            val hasSerializer =
                value is Event ||
                    value is Filter ||
                    value is Message ||
                    value is Command ||
                    value is EventTemplate<*> ||
                    value is Rumor ||
                    value is BunkerRequest ||
                    value is BunkerResponse

            if (!hasSerializer) {
                throw IllegalArgumentException(
                    "No Jackson serializer is registered for ${value::class}, so Jackson would " +
                        "serialize it reflectively and emit obfuscated field names in a release " +
                        "build. Register one in JacksonMapper, or route the type at " +
                        "KotlinSerializationMapper in OptimizedJsonMapper.",
                )
            }
        }

        /**
         * `T::class.java`, not a TypeReference — the same reason the JavaTypes above
         * are built from Class objects. A TypeReference reads its type argument back
         * off the anonymous subclass's generic superclass, which R8 in full mode does
         * not keep, and it throws "TypeReference constructed without actual type
         * information" on device. This path reaches it through the NIP-46 bunker
         * (NostrConnectEvent, RemoteSignerManager, NostrConnectLoginUseCase), which
         * needs a remote signer to exercise and so survived the first device run.
         *
         * Erasure costs nothing here: [checkRegistered] has already limited T to the
         * ten registered classes, and each one is answered by a StdDeserializer
         * registered against that exact Class. Jackson never has to infer a type
         * argument, so there is none to lose.
         */
        inline fun <reified T : OptimizedSerializable> fromJsonTo(json: String): T {
            checkRegistered(T::class)
            return mapper.readValue(json, T::class.java)
        }

        inline fun <reified T : OptimizedSerializable> fromJsonTo(json: InputStream): T {
            checkRegistered(T::class)
            return mapper.readValue(json, T::class.java)
        }

        fun toJson(event: Event): String = EventManualSerializer.toJson(event.id, event.pubKey, event.createdAt, event.kind, event.tags, event.content, event.sig)

        /**
         * Pretty-printed event JSON for human inspection. Uses the
         * [InliningTagArrayPrettyPrinter] already configured on the
         * mapper so each tag array stays on its own line (no nested
         * line-per-element noise) and the seven event fields get their
         * own indented lines. Re-canonicalisation is the caller's
         * problem — this output is not canonical NIP-01.
         */
        fun toJsonPretty(event: Event): String =
            mapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    EventManualSerializer.assemble(
                        event.id,
                        event.pubKey,
                        event.createdAt,
                        event.kind,
                        event.tags,
                        event.content,
                        event.sig,
                    ),
                )

        fun toJsonPretty(template: EventTemplate<*>): String = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(template)

        fun toJson(event: ArrayNode): String = mapper.writeValueAsString(event)

        fun toJson(event: ObjectNode?): String = mapper.writeValueAsString(event)

        fun toJson(value: OptimizedSerializable): String {
            checkSerializable(value)
            return mapper.writeValueAsString(value)
        }

        fun toJson(tags: TagArray): String = mapper.writeValueAsString(tags)
    }
}
