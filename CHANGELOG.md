<a id="v1.05.1"></a>
# [Release v1.05.1: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v1.05.0) - 2025-01-08

- Fixed mixed DMs between logged in users.
- Fixed draft screen click to edit post.

<a id="v1.05.0"></a>
# [Release v1.05.0: Bookmark Lists and WoT Scores](https://github.com/vitorpamplona/amethyst/releases/tag/v1.05.0) - 2025-01-08

#Amethyst v1.05.0: Bookmark Lists, Voice Notes, and WoT Scores

This release introduces Bookmark List management, a complete overhaul of Voice Notes/YakBaks,
and the debut of Web of Trust (WoT) scores for a safer social experience.

This version adds support for creating, managing, deleting, and viewing multiple bookmark lists,
which include both public and private members. You will find an improved "Bookmarks" menu option in
the sidebar and extra bookmark options in the context menu of each post, allowing you to add posts
directly to one or more individual lists.

The Voice Notes UI has been redesigned to allow recording directly within the new Post Screen and a
dedicated Voice Reply screen. Users can record a new voice message, preview it with waveform
visualization, re-record if needed, select a media server, and post the reply. You now have full control.

Amethyst now supports Trusted Assertions. By connecting to a WoT provider, you can see trust scores
and verified follower counts directly on user pictures. This helps filter signal from noise, identifying
reputable accounts to follow, which DMs to open, and which notifications to prioritize. To activate
this, you will need to find a provider capable of computing these scores. While providers are
currently limited and resource-constrained, we hope more will bring their own algorithms to Nostr over time.

Quartz received a significantly improved database engine capable of sub-microsecond queries using Android's
default SQLite database. The engine is optimized for mobile environments, using as little memory as
possible to avoid impacting other apps.

In the background, we have begun building Amethyst Desktop. While much work remains, the goal is a
standalone, mouse-first application that moves away from mobile-centric UI layouts.

New Features
- Trusted Assertions: Added support for trust scores displayed on user profile pictures
- WoT Followers: Displays verified follower counts in user profiles
- Bookmark Lists: Full support for custom lists by @npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k
- Relay Information: New UI with expanded NIP-11 feature support
- Voice Notes & Replies: Redesigned experience by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Profile Banner: New default banner by @npub1tx5ccpregnm9afq0xaj42hh93xl4qd3lfa7u74v5cdvyhwcnlanqplhd8g
- Native Links: Intercept njump, yakihonne, primal, iris.to, zap.stream, and shosho.live to open directly on Amethyst by @npub1lu4l4wh7482u8vq0u63g2yuj8yqjdq8ne06r432n92rnd22lspcq0cxa32

Improvements:
- New in-memory graph-based cache scheme; moved reports and WoT scores to this new system
- Disabled top bar reappearance to prevent feed shifting when navigating between pages
- Lenient Kotlin Serialization to prevent crashes from malformed JSON;
- Removed expired addressable events from cache
- Moves reports from the old caching system to the new Graph-based one.
- Reverted to a 500-post load limit for Profile screens to handle high-reply accounts
- Moved the QR Code screen from a Dialog to a full Route.
- Re-adds name as a tagging name to the profile edit page.

Performance:
- Faster event id checker by serializing, sha256 hashing, and ID comparison without creating any intermediary buffers.
- Faster event JSON parsers by avoiding new variables and thus garbage collection calls
- Faster tag array Deserializer
- Manages the pool state without having to loop through relays, saving some milliseconds of processing.
- Adds a cache system for WoT scores
- Improved Compose stability for video UI

BugFixes:
- Fixes JSON serialization of UTF-8 Emoji surrogates for compatibility with standard Nostr implementations
- Improves error message on zap configuration errors with detailed NWC URI by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Centers QR dialog content and reduce excessive top spacing by @npub1qqqqqqz7nhdqz3uuwmzlflxt46lyu7zkuqhcapddhgz66c4ddynswreecw
- Closes subscriptions when ending them on NostrClient instead of waiting for them to finish
- Requires a relay to be an outbox/inbox relay to be able to NOTIFY a user of a payment
- Improves the speed of parsing of invalid kinds inside an address string
- Fixes count not working for LIMIT queries in the DB
- Fixes icon bug with incorrect resource id by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Fixes missing updates to the feed when the top list is not yet available locally
- Fixes List of supported NIPs as Integers on NIP-11 by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Fixes ConcurrentExceptions on event outboxes

Desktop:
- Base Compose Multiplatform Desktop App with posts and global/following feeds by @npub12cfje6nl2nuxplcqfvhg7ljt89fmpj0n0fd24zxsukja5qm9wmtqd7y76c

Web:
- New website by @npub18ams6ewn5aj2n3wt2qawzglx9mr4nzksxhvrdc4gzrecw7n5tvjqctp424

Quartz:
- Adds support for Trust Provider lists and Contact Cards for NIP-85
- Early support for Payment targets as per [NIP-A3](https://github.com/nostr-protocol/nips/pull/2119) by @npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Initial support for NIP 46 by @npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Adds support for fast MurMur hash 3 64 bits
- Adds a nextLong secure random method
- Removing the generalist approach of ptag-mentions
- Removes deprecated fields in UserMetadata
- Removes compose bom from Quartz to avoid unnecessary dependencies.
- Removes datetime dependencies from Quartz
- Adds dependency on coroutines directly (instead of through compose runtime)
- Removes old secp256 target dependencies
- Adds Default scope for NostrClient and Relay Authenticator

Quartz-Event Store:
- Moves from text tags to probabilistic 64-bit MurMur Hash3 integers for performance
- Moves from range index queries to kind,pubkey queries by default.
- Adds simpler SQL queries for specific simple Nostr filters
- Expose SQL query plans, vacuum, and analyse to lib users
- Implements AND Tag queries from [NIP-91](https://github.com/nostr-protocol/nips/pull/1365)
- Implements GiftWrap deletions by p-Tag with deletions and vanish requests
- Offers several indexing strategy options to users.
- Adds several test cases that verify not only the SQL but also the indexes used
- Exposes raw queries that return columns for relays that might not need the tag array
- Forces the use of the index on Addressables and Replaceables on triggers
- Fixes duplicated events being returned from the DB
- Fixes unused Or condition in the SQL builder
- Refine the structure of the module classes for the DB
- Removes the Statement cache since statements are not thread safe
- Creating interfaces for multiple EventStores

Code Quality:
- Updates kotlin, compose, multiplatform, activity, serialization, media3, mockk, secp256, tor, androidxCamera, stdlib
- Adds a compose stability plugin to allow traces in debug
- Updates to the latest Zapstore config
- Updates quarts instructions in the ReadMe.

Updated translations:
- Czech, German, Swedish, and Portuguese by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Polish by @npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm
- Hungarian by @npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp @npub1dnvslq0vvrs8d603suykc4harv94yglcxwna9sl2xu8grt2afm3qgfh0tp
- Hindi by @npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6
- Slovenian by @npub1qqqqqqz7nhdqz3uuwmzlflxt46lyu7zkuqhcapddhgz66c4ddynswreecw
- Spanish by @npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903
- Latvian by @npub1l60stxkwwmkts76kv02vdjppka9uy6y3paztck7paau7g64l687saaw6av
- Dutch by @npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd
- French by @npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz and Alexis Magzalci
- Chinese by @npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h

<a id="v1.04.2"></a>
# [Release v1.04.2: Fix for Google Play](https://github.com/vitorpamplona/amethyst/releases/tag/v1.04.2) - 2025-11-15

Quick release for Google.

<a id="v1.04.1"></a>
# [Release v1.04.1: Bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v1.04.1) - 2025-11-15

#Amethyst v1.04.1: Bug fixes

- Fixes crashing when starting
- Fixes hashtag unfollowing for mixed case tags
- Fixes release Id for the zap the devs button
- Fixes quartz version for a release to Maven
- Fixes disappearing of the Zap the Devs Manual Payment screen
- Fixes back button of the Zap the Devs Manual Payment screen staying behind the status bar

Translations:
- Polish by @npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm
- Hungarian by @npub1dnvslq0vvrs8d603suykc4harv94yglcxwna9sl2xu8grt2afm3qgfh0tp

Download: http://amethyst.social

<a id="v1.04.0"></a>
# [Release v1.04.0: List Management](https://github.com/vitorpamplona/amethyst/releases/tag/v1.04.0) - 2025-11-15

#Amethyst v1.04.0: List Management

This version adds support for creating, managing, deleting, and viewing follow lists, which include
both public and private members. We've also added similar UI to support for creating, managing,
deleting, and viewing follow packs from following.space, allowing you to assemble following lists
not only for yourself, but also as a starter packs for other users. You will find a new "My Lists"
menu option in the side bar and Follow buttons when extended behavior to add users directly into
lists or packs.

This version also introduces significant improvements to the quality of video compression and hash
checks during media upload/download, along with new codecs and a new image gallery for displaying
sequences of images in posts. The startup loading of outbox relay lists and user metadata for large
follow lists (> 1000 people) has been significantly improved with new mechanisms to search for missing
outbox relay lists.

The Top Bar filter "All Follows" now merges all follow lists, follow packs, following hashtags,
following geotags, and following communities into a single feed. Two new options were also added:
"All User Follows," which includes only the main and other follow lists, and "Default Follow List,"
which is the standard follow list used by every client.

Finally, we completed our Quartz migration to Kotlin Multiplatform, added significant
performance improvements when processing events and running cryptographic procedures, as well
as new ease-of-use extensions.

New Features:
- People List creation and management by @npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k
- Follow Pack creation, management and feed view
- Image gallery in posts by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Finishes migration of Quartz to Kotlin Multiplatform
- New Filters in the top nav bar
- Performance improvements across all features

Improvements:
- Adds support to rejection replies from NIP-55 signers by @npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Adds live/offline indicator to live bubbles by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Refines the video compression procedure by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Replaces MP4 parser libraries with native MediaMuxer / MediaCodec by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Adds a H.265/HEVC codec to reduce file size by up to 50% while maintaining the same quality by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Optimizes video file structure by moving metadata to the beginning, so videos start playing more quickly by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Adds "All User Follows" feed filter in the top nav bar that removes hashtags, communities and geotag follows
- Adds "Default Follow List" feed filter in the top nav bar that contains only Kind 3 follows
- Shows a dialog to select a signer when using multiple signers are present by @npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Saves bandwidth by avoiding constant REQ updates before EOSEs come back in a subscription
- Changes the following icon on top of user's pictures to include follows in all lists and follow sets
- Removes non-outbox relays from the outbox lists
- Adds support bigger, up to 4GB, payloads in NIP-44
- Restructures the default REQ limits from each relay in all feeds
- Adds a guarantee incoming message order to OkHttp websocket implementations to avoid EOSE mismatch
- Only downloads DMs and Drafts if the account is writeable / nsec is present
- Adds support for copying/cloning follow lists in the UI.
- Adds a default relay list for indexers in new accounts with local backup
- Smoothes the disappearing effect for the Top and Bottom navigation.
- Adds support for NWC deep links and removes hardcoded Alby integration
- Adds a missing outbox relay list popup on posting new notes
- Adds a missing inbox relay list check on notification screens
- Migrates to njump.to to disperse shareable links

Quartz:
- Migrates Quartz from Android to CommonMain (Kotlin Multiplatform)
- Adds a maven publishing to ship quartz
- Fully converts OpenTimestamp Java codebase to Kotlin, migrating the sync and async HTTP call interfaces to OkHttp and coroutines
- Redesigns parsing of relay commands, messages and filters for performance in Jackson.
- Uses KotlinX Serialization when speed is not a requirement
- Migrates all Jackson field annotations to Kotlin Serialization
- Migrates Regex use in Quarts to Kotlin's Regex class
- Migrates Base64 library from Android to Kotlin
- Migrates UUID library from Android/Java to Kotlin
- Migrates LRUCache usage from Android/Java to Kotlin collections
- Migrates all String to bytearray conversions to Kotlin methods
- Migrates all System.arraycopy calls to kotlin native ones.
- Separates parsing code from the data classes in Companion objects
- Exposes Rfc3986 normalizations to each platform.
- Exposes URI parsing classes to each platform.
- Exposes URL Encoders to each platform.
- Exposes BigDecimal to each platform.
- Exposes the Url Detector to each platform.
- Exposes Mac instances to each platform
- Exposes Diggest instances to each platform.
- Exposes BitSet to each platform.
- Exposes GZip to each platform.
- Exposes Secp256k1 to each platform.
- Exposes SecureRandom to each platform.
- Exposes Time in seconds to each platform.
- Exposes the LargeCache to each platform.
- Exposes AES CBC and AES GCM encryption/decryption to each platform
- Migrate test assertions to Kotlin Tests
- Exposes Address class to each platform because of the Parceleable requirement in Android
- Creates our own ByteArrayOutputStream.
- Removes threadsafe features inside our Bloomfilters because we don't need that consistency
- Migrates UserMetadata parser from Jackson to Kotlin serialization
- Removes @Static methods in each tag.
- Adds an EventTemplate serializer
- Removes the need for KotlinX Datetime
- Adds support for LibSodium in the JVM desktop platform
- Creates a shared test build for iOS targets
- Increases the Bloom filter space to better use hints in the app.
- Removes support for iOS in x86
- Creates a performant JacksonMapper just for NIP-55, which stays in the Android build only.
- Keeps the event store in the android build due to the SQL Lite dependency
- Removes @syncronized tags in favor of Mutexes.
- Improved sendAndWaitForResponse NostrClient method to properly account for returns from each relay.
- Removes the need for GlobalScope and async calls
- Removes the dependency on Jackson's error classes across the codebase.
- Moves the hint to quote tag extension methods to their own packages.
- Migrates NIP-06 and Blossom uploads to use Kotlin Serialization
- Adds ease of use functions for the downloadFirst nostr client extension method
- Refactors error logging in BasicRelayClient
- Starts NostrClient in active instead of waiting for a connect() call
- Adds initial test cases for NostrClient and extensions
- Adds an option to ignore failed reconnection delays that should be used when the network settings change.
- Adds a build template option for NIP-42 AUTHs
- Moves quartz to Java 21 due to binary inconsistencies between the multiple builds of KMP (tests conflicting with main)
- Adds support for COUNT relay messages
- Treat COUNT as query only, not subscriptions in the NostrClient
- Moves statistics collection out of the inner classes to be an external option for app developers instead.
- Restructuring relay classes to maintain order of incoming messages for relay listeners
- Defers all processing of incoming messages to coroutines via channels, freeing OkHttp's thread as soon as possible.
- Simplifies the main relay class by using attached listener modules for each function of the relay client.
- Migrate defaultOnConnect calls to become listener based and moved to NostrClients
- Coordinates REQs so that if an update is required to be sent but the server has not finished processing events, waits for it to finish and sends it later as soon as EOSE or Close arrives
- Correctly maintain the local and server state of each Req.
- Avoid subsequent REQ updates before EOSE or CLOSE calls.
- Refactors NostrClient authenticators to do complete operation as an optional module
- Breaks down Relay Client modules (Auth, Reqs, Counts, Event submissions) in the Relay Pool class.
- Creates listeners just for REQ subscriptions
- Move statistics to outside the base relay class as a listener
- Move logs to outside the base relay class as a listener
- Better structures a Standalone Relay client
- More appropriately communicate errors to the listeners
- Remove relay states on listeners, move each to its own method
- Removes the hardcoded Dispatchers on Quartz
- Adds streaming hash utility function, follow the existing pool/worker design
- Adds fast search for events and addresses in the Deletion Event
- Adds an update method for to create a new event template from an event.

Fixes and UI Improvements:
- Changes the DVM feed to sort by follows that liked or zapped the DVM
- Changes the icon of account preferences to translation for now
- Improves click and long press interactions with the relay list item and status bar items
- Fixes the visual references to communities and hashtags in the top right of the post
- Removes disappearing top and bottom bars from settings screens
- Fixes tall top bars on Ephemeral and Public Chat rooms
- Fixes lack of live stream name on the top bar
- Fixes animations to navigate from and to list screens
- Fixes cursor behind the keyboard when typing long texts.
- Fixes line wrap in the relay info top nav bar title
- Moves message button to Profile Actions
- Fixes User profile banner being off place in short images
- Also fixes spacing of the Follow button
- Adds an option to render a user gallery from hex keys instead of full User objects
- Adds context to the highlights
- Increases the contrast of placeholder text
- Refines performance of the QuickAction menu bar
- Fixes hidden words not being applied to NIP-04 DMs on notifications
- Fixes not loading some event kinds in notifications
- Fixes crash when updating a metadata with null name
- Fixes crash when attempting to share an image that is still loading by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Fixes disappearing stats on relay screen by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Fixes proper switch between single and two-pane layouts on rotation by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Fixes sendAndWaitForResponse never receiving a response by @npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Fixes NWC URI parsing bug by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Fixes bookmark removal from Private/Public removing from both by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Fixes OutOfMemoryError for large file uploads by  @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Fixes DecryptZapRequest CommandType by @npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Fixes location being added to note even after deselecting it by @npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Fixes crash when trying to parse unparseable NIP-11s
- Prevents resource leaks with file streams by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Removes any relay url that has a percent-encoded null byte, regardless of size
- Forces streaming url online check when the stream is offline but the user enters the chat
- Fixes duplicated loading of NIP-11 relay info by different parts of the screen
- Fixes the new Video events as non replaceable, while keeping the old ones in the replaceable
- Fixes crash on starting when the contentResolver is not ready yet.
- Fixes addressable deletions deleting not only the past when updating feeds
- Fixes long form previews when missing the author's picture
- Fixes feed filter update when changing the top nav filter
- Fixes crash when sorting with the same createdAt in the discovery feeds
- Fixes reply routes when clicking in the Conversations tab when the event is a PublicChat, LiveStream or Ephemeral Chat
- Fixes livestream chats appearing on home bubbles after the live stream is finished
- Fixes not sending the live stream events anywhere when the stream doesn't have a relay set declared
- Fixes animations when selecting Tor options in the privacy screen
- Fixes animations jumping when loading privacy screen
- Fixes the use of index relays and search relays to load users and events as well as become the default for global feeds.
- Fixes mark as read when drafts are the latest message in the chat
- Fixes showing blog posts in the future in the Discovery reads
- Fixes crashing when comparing int and long in Live Events comparator
- Fixes recompositions of subject add-on to text on rendering
- Fixes crash on trying to change dont translate from options
- Fixes NPE on the ln invoice callback with errors parser
- Fixes parsing encrypted NIP-28 chats
- Fixes disappearing relay stats (larger LRU cache)
- Fixes sendAndWait nostr client coroutine that was waiting forever
- Fixes lack of req by forcing an updateFilter at the start of new subs with the client.
- Fixes bug that skipped loading follow's metadata on startup
- Fixes issues when unfollowing a hashtag from old contact lists
- Fixes relay list flickering bug
- Fixes crash when checking and creating new users from `p` tags.
- Fixes the author of the highlight
- Fixes scope lifecycle of media uploads to avoid cancellation while uploading
- Fixes parser for null and default values from NIP-55 using Jackson
- Fixes relay icons not showing up when sending DMs
- Fixes imeta parsing with multiple urls
- Fixes relay-specific global feed matching incoming note checks
- Fixes video progressive download by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Fixes several usages of Reflection when serializing classes
- Fixes a bug on loading event test database for the HintBloom filter test
- Forces relay status updates when connecting and disconnecting
- Only shows live stream bubbles that are not playing 24/7 with active follows in the past 15 minutes.
- Opens Follow Packs on following.space

Performance:
- Restructures the parser and serialization of the relay messages and commands for performance
- Speeds up the generation of Bech32 addresses
- Migrates memory counters from Long to Int
- Avoids using JSON parsers with DataStore to speed up loading time (loading the parser itself takes ~300ms)
- Adds new benchmarks for NIP-44 operations
- Reduces memory consumption for NIP-44 operations, avoiding GC
- Faster Hkdf functions with less array copying and allocations (which can be impactful if the ciphertext is large)
- Faster Mac calculations by avoiding array assemblies before calling the function
- Faster Hash check calculations avoiding the creation of a separate bytearray to compare ids
- About 30-40% event hashing performance boost by building the json by hand and skipping string encoders when not needed.
- 30% Faster isHex for strings with precisely 32 bytes.
- Loads the main account in parallel faster before the screen needs it
- Merge expanding and checking HMac functions to avoid re-creating the Mac instance.
- Separates EOSE cache for drafts alone.
- Speeds up the loading of users in follow lists on start up
- Speeds up OkHttp startup
- Adds submap index queries to the addressable large cache
- Performance improvement for the address serializer.
- Adds a default cut off for notifications from random relays to 1 week ago.
- Increases local video disk cache to 1GB
- Defers the initialization of disk caches for videos and images to a few seconds after loading
- Only listens to notification feed changes when the UI is visible
- Separates a relay failure tracker to a module
- Caches the User object for each Account to avoid being deleted by the soft reference on Local Cache
- Moves all Dispatchers.Default to the IO threadpool because of the amount of Synchronized code in dependencies blocking heavy threads
- Only download reports from the author's outbox relay if we have it (don't use our default bootstrapping relays for reports)
- Keep a local cache of following geoshahes and hashtags in lowercase.
- Don't intern signatures, content and stringified tags since they are not usually duplicated
- Unifies the parser for the tag array across the app
- Adds simplified extensions to create Requests from NostrClient
- Adds a Start request callback to the request listener
- Adds a req that returns the list of events in order of arrival
- Adds a flatten straight into a set utility
- Adds a decrypted people and follow list cache on the account class and updates TopNav to use the new caching
- Creates slim ListItem composables
- Marks address, user states, edits and channel metadata view models as Compose Stable
- Removes unnecessary list of icons drawer rows, which affected Compose stability
- Improves the composition of NIP-05 lines
- Adds a pre-parser to find image galleries and video links before rendering.
- Adds the user outbox relays when loading addressables by that user.
- Avoids sending CLOSE to subs that are already closed.
- Rejects additional urls with %20 and fixes "Wss" ones.

Code Quality
- Reduce http max requests when in emulator to avoid crashing by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Resolve intermittent CI build failures  by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Fix benchmark apk location by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Change from runBlocking to runTest where appropriate by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Reduce errors in log: concurrent modification exception by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Moves the precision of mills to seconds on the isOnline Check
- Migrates to use suspending routines for OTS, statuses and edit flows
- Unify outbox calculation in the RelayOutbox loader
- Refactors zap the devs card view
- Creates an interface for NostrClient strategies
- Adds an isMobileOrFalse flow in connectivity to speed UI updates
- Removes one of the Application dependencies in viewmodel
- Changes the Theme class to only take the preferred theme directly.
- Adds dependency on NWC to Account to avoid linking directly with the Application class
- Migrates Account management to an Application lifecycle to prepare for multi-account screens
- Establishes a scope for each account loaded so that flows can be killed on sign off
- Moves the event processor to the Account class
- Creates an interface for the DualHttpClientManager to allow IDE previews
- Removing the dependency on an application class from AccountViewModel
- Removes dependency on the viewModel on AccountFeedStates
- Moves account saving from StateViewModel to Account
- Removes dependency on the application class from DecryptAndIndexProcessor
- Changes the name and singleton of the nav to avoid confusing the auto import
- Switches account cache to a flow to allow observers
- Removes dependency in the Amethyst class from Playback calculator
- Removes the dependency on windowSizeClass and displayFeatures to be passed in the global preferences of the app (which is activity independent)
- Moves the OTS processor from Account's decrypt process to Application since it doesn't need the account information.
- Separates Application dependencies into an AppModules class to create only after the OnCreate event.
- Switches TorSettings to be per Application and not per Account anymore
- Since TorSettings is now global, moves the okHttpClient determinations out of the Account-based classes into the Application.
- Since TorSettings is now global, set's up Coil's image loader only once when creating the Application
- Moves UISettings state to App Modules instead of viewmodel
- Migrates TorSettings and UISettings to DataStore
- New tor evaluator service for relay connections now uses all account's trusted relays and dm relays at the same time.
- Migrates composable-state-based UISettings to Flow-based UI settings, while observing connectivity status
- Removes the displayFeatures and windowSizeClass from the shared model
- Fixes not requesting Notification Permissions for APIs older than Tiramisu in the FDroid flavor
- Moves the NIP-11 document cache from singleton to the App Modules
- Avoids using AccountViewModel to check NIP-11 Relay documents
- Moves the UI Settings usage in composables to functions that do not observe the state since they don't need to refresh the screen when changed.
- Refactors UI Settings screen to separate components and remove the sharedViewModel
- Only starts Internal Tor if that option is selected in the TorSettings.
- Turn TorSettings into a data class to observe changes to it
- Drops the SharedPreferences ViewModel to use UISettingsFlow directly from App Modules
- Reorganizes OTS Events after simplification of the OkHttp based on TorSettings.
- Applies memory trimming service to all logged-in accounts at the same time
- Adds a test for native vs libsodium chacha20
- Added test for emulator to set maxRequests to 128 to prevent crashing emulator by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Dumps the number of relay hints urls in the DB
- Creates our own Coil Logger to minimize trash stacks in the logs
- Adds a declared outbox lists for follows state
- Removes the deprecated hacks to store communities, hashtags and geohashes on Contact Lists
- Updates deprecated Clipboard manager
- Updates emoji and user autocomplete state to use Account and avoid linking AccountViewModel
- Refactors many viewModels to avoid using callback lambdas
- Correctly marks EOSE for filters that are aligned with the Req State from NostrClient
- Changes User loading features in a tentative to make them faster since they are used by all functions in the app.
- Creates an Account follow list per Relay state that only includes shared relays as a better source of functioning relays

Deprecations:
- Removes fmt.wiz.biz from bootstrap relays
- Removes void.cat from default servers by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Removes filestr share links
- removes zapstr.live share links

Updated translations:
- Czech, German, Swedish and Portuguese by @npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Hindi by @npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6
- Slovenian by @npub1qqqqqqz7nhdqz3uuwmzlflxt46lyu7zkuqhcapddhgz66c4ddynswreecw
- Polish by @npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm
- Hungarian by @npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp @npub1dnvslq0vvrs8d603suykc4harv94yglcxwna9sl2xu8grt2afm3qgfh0tp
- Spanish by @npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903
- Latvian by @npub1l60stxkwwmkts76kv02vdjppka9uy6y3paztck7paau7g64l687saaw6av
- Dutch by @npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd
- French by @npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz
- Chinese by @npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h
- Thai by @npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e
- Persian by @npub1cpazafytvafazxkjn43zjfwtfzatfz508r54f6z6a3rf2ws8223qc3xxpk

Download: http://amethyst.social

<a id="v1.03.0"></a>
# [Release v1.03.0: Built-in video recording](https://github.com/vitorpamplona/amethyst/releases/tag/v1.03.0) - 2025-09-02

#Amethyst v1.03.0: Built-in video recording

New Features:
- Adds "record and post video" button to new Post Screens by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Updates the User Profile's Relay List to an outbox version
- Activates live streams to the top feed bubble
- Enables the use of custom TextToSpeech Engines

Improvements:
- Improves the list of visible authors on live stream bubbles
- Adds the error status to the URL normalizer to avoid checking it again
- Adds a follow list state from kind 3 to keep following users in memory.
- Moves from RelationshipStatus to ContactCard as per NIP update

Fixes:
- Removes crashing relay URIs with null-encoded bytes (%00)
- Reverts to using androidLibrary plugin for Quartz until KMP publishing guidance is updated. by [@npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k](https://github.com/npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k)
- Fixes product title when in a quoted post
- Fixes live flag for streaming when the URL is not available anymore.
- Fixes bug on opening the Relay Settings page with duplicated Trusted and Blocked relays.
- Avoids NPEs in the maxOfOrNull iterator with concurrent lists
- Assemble NIP-17 Crash Report properties as a table and crash as a code block
- Improves the design of the Crash Report permission screen with a cancel button.
- Adds basic support for expirations in DMs

Updated Translations

- Polish by [@npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm](https://github.com/npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm)
- Hungarian by [@npub1dnvslq0vvrs8d603suykc4harv94yglcxwna9sl2xu8grt2afm3qgfh0tp](https://github.com/npub1dnvslq0vvrs8d603suykc4harv94yglcxwna9sl2xu8grt2afm3qgfh0tp)

Download: http://amethyst.social

[Changes][v1.03.0]


<a id="v1.02.1"></a>
# [Release v1.02.1](https://github.com/vitorpamplona/amethyst/releases/tag/v1.02.1) - 2025-08-29

- Upgrade AGP to 8.12.2
- Sets Quartz to Java 1_8
- Creates a bootstrap relay list specifically for inbox relays
- Register lists as Account fields to avoid GC of user settings.
- Fixes inability to delete relays from certain relay lists.

[Changes][v1.02.1]


<a id="v1.02.0"></a>
# [Release v1.02.0 NIP-17 Crash report DMs](https://github.com/vitorpamplona/amethyst/releases/tag/v1.02.0) - 2025-08-28

#Amethyst v1.02.0: NIP-17 Crash report DMs

- Adds a crash interceptor and offers to send crash reports via NIP-17
- Fixes startup crash on Android 12 or earlier
- Removes old DB migration
- Fixes crash when pasting an invalid NIP-47 URI into the zap settings
- Fixes crash when NWC doesn't return an invoice preimage
- Fixes crash when loading a user with a null name
- Fixes readonly crash on DVM requests
- Speeds up first-time logins by not waiting for Tor
- Fixes a parser benchmark
- Moves test assets to resources to make it work with kmp
- Migrates Quartz to a KMP project
- Converts OpenTimestamps code from Java to Kotlin
- Moves OTS OkHttp setup to Quartz
- Migrates NIP-49 from Java to Kotlin
- Removes nostr.bg from bootstrap relays
- Updates translations

Download: http://amethyst.social

[Changes][v1.02.0]


<a id="v1.01.0"></a>
# [Release v1.01.0: New Community Implementation](https://github.com/vitorpamplona/amethyst/releases/tag/v1.01.0) - 2025-08-27

Features:
- Upgrades NIP-72 communities to use NIP-22 comments as root posts
- Adds moderation queue feed
- Adds in-app approval procedures
- Adds drafts for community posts
- Redesigns the "about us" section of communities.

Fixes:
- Fixes accessibility TalkBack issues
- Fixes the edit draft button in the long-press menu
- Fixes the inability to delete relays from some lists.
- Fixes unwanted NOTIFY requests from other people's relay lists
- Fixes some DM push notification issues
- Fixes notification registration for first-time logins
- Avoids sending expired events to the relays
- Fixes draft deletes staying in some feeds
- Intercepts backhandler to save drafts
- Moves draft deletion calculations out of the UI thread


[Changes][v1.01.0]


<a id="v1.00.5"></a>
# [Release v1.00.5](https://github.com/vitorpamplona/amethyst/releases/tag/v1.00.5) - 2025-08-23

- Fixes a crash from multiple relays in the same line when following two specific users
- Updates translations

[Changes][v1.00.5]


<a id="v1.00.4"></a>
# [Release v1.00.4](https://github.com/vitorpamplona/amethyst/releases/tag/v1.00.4) - 2025-08-22

- Fixes the new post screen not closing after posting and rejecting drafts.
- Fixes the disappearance of drafts.
- Improving loading speeds by using a separate OkHttp threadpool for DM relays and another for media.
- In case a user does not have an outbox list, it defaults to all hints seen for that user.

[Changes][v1.00.4]


<a id="v1.00.3"></a>
# [Release v1.00.3: More fixes](https://github.com/vitorpamplona/amethyst/releases/tag/v1.00.3) - 2025-08-22

- Fixes the lack of feed update for those who didn't follow any community.
- Avoids parsing AI-bad NIP-28 objects
- Don't try to decrypt appData unless it is a writeable account
- streamlines function calls on AccountViewModel
- Removes the HEAD from the release build name

[Changes][v1.00.3]


<a id="v1.00.2"></a>
# [Release v1.00.2: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v1.00.2) - 2025-08-21

Fixes follow/unfollow from hashtags and geohashes
Migrates top nav list to hashtag, geohash and community lists
Fixes community join/leave crash
Fixes branch name
Avoids crash when lacking google services in the play version.
Fixes crash when typing a new post without permissions to do a draft post.

[Changes][v1.00.2]


<a id="v1.00.1"></a>
# [Release v1.00.1: Fix app name](https://github.com/vitorpamplona/amethyst/releases/tag/v1.00.1) - 2025-08-21

fix app name

[Changes][v1.00.1]


<a id="v1.00.0"></a>
# [Release v1.00.0: Full Outbox](https://github.com/vitorpamplona/amethyst/releases/tag/v1.00.0) - 2025-08-21

#Amethyst v1.00.0: Full Outbox

This version completes our migration to the outbox model, where the app dynamically manages the relay list used to pull posts from your follows' own relay lists. By default, the app will connect to relays that aren't in your lists through our embedded Tor. Normal usage will connect to hundreds of relays. Many of them will fail, and that's ok. Nostr has baked-in redundancy; these failures won't affect your experience.

New relay lists were added to the UI to help you manage how the app works. Specifically, you can now block relays and add trusted relays. Trusted relays will connect outside of Tor, which is faster, but allows those relays to see your IP. You should only add relays there if you trust the relay operator. Proxy relays (like filter.nostr.wine) can be added to the proxy list. After that, the app will only use those relays to download the content for your feeds, disabling the outbox model. DMs and other non-outbox functionality will still use their own relays. Broadcasting relays can be added to push your events out there. Every new event from the app will be sent to all broadcasting relays. Finally, the new Indexer list allows you to choose which relays to use to find users, like purplepages.es.

For users of our Quartz library, we have finished all of the work to change the library's mindset from a fixed list to a dynamic pool of relays. Now, each NIP has its own dedicated folder and defines its own tags and caching structures. This expansion allows us to add diverse functionalities such as relay clients, relay servers, event builders, Nostr filter builders, caching systems, deletion and event hint indexers, helper functions, and more—all specifically tailored to each individual NIP. This modular approach creates the space to develop each NIP independently and integrate them into Amethyst as distinct modules, while still sharing Amethyst's main relay and cache engine when necessary. We expect fewer breaking changes as we move forward with it. At some point, Quartz will move to its own repository and be converted to a Kotlin Multiplatform project for each NIP/module. This will allow us to build demo/testing applications for each NIP in the same repo.

This version adds support for:
- YakBak Voice Messages
- Picture-in-Picture pop-ups
- Public Messages
- Coolr.chat's Ephemeral Chats
- Follow packs
- Reads feed in the discovery tab
- Hidden cashu tokens in emojis

Features:
- Reengineered relay, relay pool, and nostr client to manage dynamic pools
- Reengineered note cache for a garbage collector-friendly version
- Reengineered media pre-loading and caching to minimize layout changes
- Reengineered decryption cache, now per account
- Reengineered chat channels cache
- Reengineered the indexing of Addresses to data classes
- Reengineered EOSE cache and managers
- Migrates to a Flow-based design for all account information and services
- Migrates to a Compose subscription model for relay filters
- Adds 90-day expiration to all drafts
- Deprecate stringified JSON in favor of tags on user metadata kind 0 events
- Adds support for live events at the top of the feed.
- Migrates Video events to non-replaceable kinds
- Migrates NIP-51 to use NIP-44 encryptions
- Migrates Chat, Community, Location, and Hashtag follows to their own lists
- Migrates to reply with NIP-22 for everything but kind 1s.
- Massively improves relay hint selections
- Removes relay picker when sending new posts
- Removes general relay list (kind3)
- Adds new relay lists: Trusted, Blocked, Proxy, and Broadcasting
- Moves most of the Dialogs to full-screen routes
- Breaks NewPostScreen and ViewModels into Screens and ViewModels for each supporting NIP
- Adds support for creating and replying to NIP-22 geo scope posts
- Performance Improvements by not re-verifying duplicated events
- Adds Content Sensitivity setting to the Security filter screen
- Adds Translation setting to a new screen.
- Extends AsyncImage to correctly use pre-loaded aspect ratio and avoid jitter
- Adds imeta tags for images and urls inside the content of the Classifieds
- Adds new default banner for empty profiles
- Finishes the migration from LiveData to Flow
- Restructures the old static datasource model into dynamic filter assemblers.
- Moves filter assemblers, viewModels and DAL classes to their own packages.
- Creates Composable observers for Users and Notes
- Unifies all Filter Assembler lifecycle watchers to a few classes
- Moves relay authentication to a coordinator class for all accounts in all relays.
- Moves the relay NOTIFY parser to its own coordinator class for all accounts
- Moves the connection between filters and event cache to its own coordinator class
- Adds support for Tor in push notifications
- Isolated Connectivity services, from Compose to Flow
- Isolated Tor services, from Compose to TorService
- Isolated Memory trimming services, from Compose to Flow
- Isolated Image Caching services, from Compose to Flow
- Isolated Video Caching services
- Isolated Logging services
- Isolated NIP-95 Caching services
- Isolated Pokey receiver services
- Isolated OkHttpClient-building services as flows
- Hold off on all DM attachments until the message is sent.
- Adds previews for any number of urls, events, and media uploads on new post screens.
- Adds zap split, zap raiser, and geolocation symbols for DMs and channel messages
- Adds picture upload for NIP-28 metadata
- Adds support for community relays on NIP-28
- Adds a pool of ExoPlayers when multiple videos are playing
- Moves DVM's last announcement restriction from 90 days to 365 days

Quartz:
- Adds a NostrClient with filter and event outbox cache
- Adds a Basic RelayClient and parsers for all relay commands
- Migrates signers from callback to suspending functions
- Migrates event create functions to builders with templates
- Migrates the filter design to a filter per relay
- Migrates hardcoded tag filters in events to the Tag's parser and assembly functions.
- Normalizes all relay URLs
- Formalizes relay hint providers per kind
- Event store support with SQLite
- Reengineered NIP-55 Android signer and its cache
- Reengineered exception handling for signer errors
- Adds support for the Request to Vanish NIP - 62
- Migrates all NIP-51 lists to the new event-tag structure.
- Migrates Drafts and NIP-04 and NIP-17 DMs to the new structure
- Migrates Bookmarks to the new structure
- Migrates NIP-56 to the newest tag structure
- Adds support for nip70 Protected Tags
- Adds full support for nip73 External Content IDs
- Adds support for NIP-48 proxy tags
- Removes the old "datasource" model
- Adds a Bloom-based hint indexer with MurMur hash
- Adds a PoW miner
- Restructures thread helpers for NIP-10
- Migrates Zap splits, zapraisers, subject, alts, and content warning to their own packages.

Dev Team:
- nostr:nprofile1qqsyvrp9u6p0mfur9dfdru3d853tx9mdjuhkphxuxgfwmryja7zsvhqpz9mhxue69uhkummnw3ezuamfdejj7qgswaehxw309ahx7um5wghx6mmd9uq3wamnwvaz7tmkd96x7u3wdehhxarjxyhxxmmd9ukfdvuv
- nostr:nprofile1qqsfnw64j8y3zesqlpz3qlf3lx6eutmu0cy6rluq96z0r4pa54tu5eqpz9mhxue69uhkummnw3ezuamfdejj7qg0waehxw309ajxzmt4wvhxjme0hynkd5
- nostr:nprofile1qqs827g8dkd07zjvlhh60csytujgd3l9mz7x807xk3fewge7rwlukxgpz9mhxue69uhkummnw3ezumrpdejz772u5wm
- nostr:nprofile1qqswc4nrhvp4lrjct0ayy0ps8f2hvj8e2guucp63dwcx6m6e8pka9fqpz4mhxue69uhhyetvv9ujuerpd46hxtnfduhszrnhwden5te0dehhxtnvdakz7qg7waehxw309ahx7um5wgkhqatz9emk2mrvdaexgetj9ehx2ap00me8jy
- nostr:nprofile1qqsv4zwtz8cuwh2mvc3zdrl5853g365t9j6mn25edlul7uz0eyzt0zcpzamhxue69uhhyetvv9ujumn0wd68ytnzv9hxgtcpzpmhxue69uhkummnw3ezumt0d5hscc6wyt

Translations:
- Czech, German, Swedish, and Portuguese by nostr:nprofile1qqsv4zwtz8cuwh2mvc3zdrl5853g365t9j6mn25edlul7uz0eyzt0zcpzamhxue69uhhyetvv9ujumn0wd68ytnzv9hxgtcpzpmhxue69uhkummnw3ezumt0d5hscc6wyt
- Dutch by nostr:nprofile1qqs82l74z7g3x8j3avpn2wrjrwn855nyvmpxa4v5pftfvtv5lrvrc5cpz9mhxue69uhkummnw3ezuamfdejj7tk0drp
- French by nostr:nprofile1qqs8av5uzf4nv2q80chrmp3mj9a9dd6zjw4fmz56hsn2gzar72rxhtcppemhxue69uhkummn9ekx7mp0qyg8wumn8ghj7mn0wd68ytnddakj7qgawaehxw309ahx7um5wghxy6t5vdhkjmn9wgh8xmmrd9skctcuvd26f
- Polish by nostr:nprofile1qqsdyfz0ewdhmgp3a4r3pxvezx5r8yalrgvjn38v2ml5qrusnv7yywgpzamhxue69uhhyetvv9ujumn0wd68ytnzv9hxgtcpz9mhxue69uhkwmn0wd68ytnrdakj7qgkwaehxw309ahx7um5wghxx7npwvh8qmr4wvhsrpff27
- Chinese by nostr:nprofile1qqsyxnuhnymz0u0xru2watmqe25vlh8vzzje9jhlsfgvsff9942gc9gpr9mhxue69uhhyetvv9ujumt0d4hhxarj9ecxjmnt9uq3vamnwvaz7tmjv4kxz7fwd4hhxarj9ec82c30qy28wumn8ghj7atn9ehx7um5wgh8w6twv5hsung0qr
- Slovenian by nostr:nprofile1qqsqqqqqqp0fmkspg7w8d305ln96a0jw0ptwqtuwskkm5pddv2kkjfcpyfmhxue69uhk6atvw35hqmr90pjhytngw4eh5mmwv4nhjtnhdaexcep0qythwumn8ghj7un9d3shjtnswf5k6ctv9ehx2ap0qy2hwumn8ghj7un9d3shjtnyv9kh2uewd9hj72epxz8
- Thai by nostr:nprofile1qqsxdhmq2cke8xk6scfyxeyfj3dyancavg6xk0v50r023gec7vsrceqppemhxue69uhkummn9ekx7mp0qyghwumn8ghj7mn0wd68ytnhd9hx2tcpz4mhxue69uhhyetvv9ujuerpd46hxtnfduhskamkgc
- Bengali by nostr:nprofile1qqsgs9hgjw87vz36jf2r83m5zree2q87zvs8s7kty9jljdz7wprytyspremhxue69uhkummnw3ezu6m0de5kueedv3jkwunpv9nzumnv9uq3kamnwvaz7tm5d4cz6un9d3shjtnrv4ekxtn5wfskgef0qy28wumn8ghj7mn0wd68yt3k8quzummjvuhsg63aw2
- Hindi by nostr:nprofile1qqs88dt78wgnzvaph6fcstfvsd98xc2qs8eg8tllwv2zlutu8ehec2cpzamhxue69uhhyetvv9ujumn0wd68ytnzv9hxgtcy8dumx
- Spanish by nostr:nprofile1qqs07tjpyvvlq9ugdpax8h3jfrpwn7kr72k3tc7ky83tggn4et9eangprpmhxue69uhkv6tvw3jhytnwdaehgu3wwa5kuef0qyghwumn8ghj7mn0wd68ytnhd9hx2tcpzemhxue69uhkumm5d9n8jtnyv9kh2uewd9hj7cn2zey
- Hungarian by nostr:nprofile1qqs88rmfrp9wmfn4qq4kslly0j8futmmrgn86mu3gkc3jvcjl97p3mcpzamhxue69uhhyetvv9ujumn0wd68ytnzv9hxgtcpz4mhxue69uhkummnw3ezummcw3ezuer9wchsz9thwden5te0wfjkccte9ejxzmt4wvhxjme0m7mtdy and nostr:nprofile1qqsxekg0s8kxpcrka8ccwztv2m73kz6jy0ur8f7jc04rwr5p44w5acsk5trt8
- Persian by nostr:nprofile1qqsvq73w5j9kw573rtff6c3fyh953w45328n3625apdwc3548gr49gsppemhxue69uhkummn9ekx7mp0q6fpv8

Download: http://amethyst.social

[Changes][v1.00.0]


<a id="v0.94.3"></a>
# [Release v0.94.3: Adds iMeta tags to GIF urls](https://github.com/vitorpamplona/amethyst/releases/tag/v0.94.3) - 2025-01-05

- Adds iMeta tags to GIF urls to optimize GIF previews
- Fixes the extra empty kind 20 post when uploading videos on the media tab
- Fix: Only close the upload screen if the video upload is a success on the Media tab
- Maintains note reaction visibility when scrolling by [@npub1xcl47srtwh4klqd892s6fzwtdfm4y03wzjfl78scmmmxg8wzsf4qftd0en](https://github.com/npub1xcl47srtwh4klqd892s6fzwtdfm4y03wzjfl78scmmmxg8wzsf4qftd0en)

Download: http://amethyst.social

[Changes][v0.94.3]


<a id="v0.94.2"></a>
# [Release v0.94.2: Fixes Tor leaks](https://github.com/vitorpamplona/amethyst/releases/tag/v0.94.2) - 2025-01-04

This release fixes two Tor leaks introduced during the migration of two APIs in v0.94.0. The new relay API contained a bug that bypassed the user's Tor preference for relays and the migration to Coil3 implemented a one-time cache for the Tor preference, preventing it from reflecting subsequent changes for image loading.

Thanks to nostr:npub17lmqmq680446scdgvv58snglr3h2phe00thqfe0twa3l8q5mzmusj6c60g for the Tor audit

Download: http://amethyst.social

[Changes][v0.94.2]


<a id="v0.94.1"></a>
# [Release v0.94.1 GIFs and Custom Emoji inputs](https://github.com/vitorpamplona/amethyst/releases/tag/v0.94.1) - 2025-01-04

This version adds a `:` command to link custom emojis on new posts and chats. Similar to the @ for user search, just start typing to find your custom emojis. When you see the list of emojis, click on the emoji to add it as an inline emoji OR click on the right button to add as a regular URL in the post.

Create your GIF and rection libraries on emojito.meme . Make sure to bookmark your and/or other people's emoji packs to add them to Amethyst's `:` list.

Happy shitposting.

Download: http://amethyst.social

[Changes][v0.94.1]


<a id="v0.94.0"></a>
# [Release v0.94.0 Encrypted Media on DMs](https://github.com/vitorpamplona/amethyst/releases/tag/v0.94.0) - 2025-01-03

Now every upload on DM chats will be encrypted to the destination's pubkey following the same spec 0xChat uses. This offers a massive update in privacy from the common "hidden link" design. The encrypted blobs are sent to NIP-96 and Blossom servers. Make sure your server accepts encrypted blobs. Sattelite and void.cat do accept. We redesigned our upload screens to allow multiple images/videos at the same time on new posts, stories and chats encryption. Error handling was also improved with the screens now allowing you to try again to a different server.

Features:
- Adds support for encrypted media uploads on NIP-17 DMs
- Integrates with Pokey's Broadcast receiver.
- Expands the Around me filter to 50km
- Shows NIP-22 replies in the replies tab of the user profile
- New upload screen for chats
- When uploads fail, the screen stays live to allow changing the server and trying again.
- Improves the padding in the layout of the gallery
- Allows multi-images posts to be displayed in the Profile gallery
- Refactors zap error message screen to allow sending messages directly to each split receiver with their error
- Adds support for multiple media uploads at the same time.
- Adds support to display PictureEvents with multiple images at the same time
- Adds QR code private key export dialog by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Added Gamestr custom hashtag icon by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Switches to the right account from push notification's click
- Adds new picture and video events to the user profile gallery
- Adds basic support for RelationshipStatus to Quartz

Fixes:
- Fixes bug that resets localhost relay settings.
- Moves to using cancellable coroutines to avoid cancelling the parent thread all together.
- Fixes the navigation padding on Samsung devices for the Shorts tab.
- Fixes the chat's input field behind Samsung's app bar on tablets
- Fixes notes appearing of replies for some recipes
- Removes the swipe to delete draft from right to left. Only left to right remains available.
- Solves crashing when a p-tag contains only "ffff"
- Fixes edge to edge issues when the keyboard is shown on the Media Server settings.
- Fixes keyboard overriding the relay settings screen
- Fixes double quotes on NIP-28 Channel messages
- Fixes cosine caching on Blurhash
- Fixes download and loading image icon not showing on posts when text overflow by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Fixes lack of notification when a new account is logged into the app and before the app restarts.
- Fixes displaying an old result when coming back to a DVM screen
- Fixes the bugs from migrating video events to imeta tags
- Removes pull to refresh from gallery

Performance:
- Increases the number of possible active video playbacks on the screen to 20
- 10x better performance on Blurhash generation
- Improves search by npubs to use all relays.

UI Improvements:
- Avoids cutting off some of the users in the Pay intent screen
- Adds toast message when the video/image starts downloading by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Remove crossfades and double composition on image loading/success
- Improves Uploading feedback for the NewPost screen
- Optimizes user search to account for names that start with the typed prefix
- Several accessibility improvements by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Simple refactoring to newer versions of the clickable text
- Migrates Video events to imeta tags
- Removes youtu.be links from the video feed.
- Checks for video file types in uppercase as well as lowercase

Code quality:
- Move relay dialog to a route by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Refactors user tagging lists to generalize them
- Updates zoomable, vico, mockk, kotlin, compose Bom, firebase, navigation compose, android camera libs and adaptive acompanist
- Refactors Ammolite to remove the dependency on OkHttp to prepare for KTor and multiplatform settings.
- Reduces the singleton coupling between Client and RelayPool.
- Removes troublesome dependency on blurhash encoder library
- Restructures contentScale for Images and Video dialogs
- Refactors Media Uploaders to improve code reuse
- Refactors iMeta usage on Quartz to move away from NIP-94
- Removes the use of nostr: uri for notifications
- Enables a new screen to be routed when new logins happen
- Removes contract of the old image picker
- Simplifies the Gallery stack
- Separates event class that manages general lists and abstracts another intermediary class that manages private tag arrays in its content.

Updated translations:
- Czech, German, Swedish and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Polish by [@npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm](https://github.com/npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm)
- Chinese by [@npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h](https://github.com/npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h)
- Slovenian by [@npub1qqqqqqz7nhdqz3uuwmzlflxt46lyu7zkuqhcapddhgz66c4ddynswreecw](https://github.com/npub1qqqqqqz7nhdqz3uuwmzlflxt46lyu7zkuqhcapddhgz66c4ddynswreecw)
- Thai by [@npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e](https://github.com/npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e)
- Bengali by [@npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t](https://github.com/npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t)
- Hindi by [@npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6](https://github.com/npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6)
- Spanish by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp) and [@npub1dnvslq0vvrs8d603suykc4harv94yglcxwna9sl2xu8grt2afm3qgfh0tp](https://github.com/npub1dnvslq0vvrs8d603suykc4harv94yglcxwna9sl2xu8grt2afm3qgfh0tp)
- Persian by [@npub1cpazafytvafazxkjn43zjfwtfzatfz508r54f6z6a3rf2ws8223qc3xxpk](https://github.com/npub1cpazafytvafazxkjn43zjfwtfzatfz508r54f6z6a3rf2ws8223qc3xxpk)

Download: http://amethyst.social

[Changes][v0.94.0]


<a id="v0.93.1"></a>
# [Release v0.93.1 Fixes Satellite's blossom upload](https://github.com/vitorpamplona/amethyst/releases/tag/v0.93.1) - 2024-12-05

- Moves to NIP-22 to reply to Interactive Stories.
- Adds amount and personalization labels to the DVM feed
- Fixes Satellite's blossom upload
- Fixes incorrect reply order when the direct reply is also included as a quote.
- Fixes image upload tests
- Fixes the bug of not having the video feed at the top when loading the app from scratch.
- Fixes screen mispositioning when rotating the phone on full-screen video/image dialogs.
- Fixes images on DVM profiles
- Fixes badge crash
- Fixes missing reactions on video feeds
- Improves performance of the Hex encoder.
- Improves the layout of the discovery feed items
- Updates Jackson, secp256k1, and AGP

Download: http://amethyst.social

[Changes][v0.93.1]


<a id="v0.93.0"></a>
# [Release v0.93.0: Blossom, Olas, Around Me feeds and Interactive Stories.](https://github.com/vitorpamplona/amethyst/releases/tag/v0.93.0) - 2024-11-27

Adds support for displaying NIP-63 Interactive Stories
Adds support for Blossom media servers
Adds support for Olas' Image feeds
Adds support for Around Me feed with posts that only show up in that location

New Features:
- New Android Image/Video Picker
- Adds support for pronouns on profile
- Migrates Video uploads from NIP-94 to NIP-71 Video events
- Migrates Picture uploads from NIP-94 to NIP-68 Picture events
- Adds support for BUD-01, BUD-02, and BUD-03
- Adds support for NIP-22 Comments
- Adds nip05 field to the hidden words filtering by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Adds support for q tags with addresses
- Adds search.nos.today to bootstrapping relays for search
- Adds DM and Search default buttons to the relay screens
- Adds hidden words filter to search, hashtag and geotag feeds
- Applies hidden words even to hashtags that were not included in the content of the event.
- Adds support for saving reactions, zaps, translations user preferences on private outbox relays

UI Improvements:
- Adds animations to the zap, reaction and boost popups by [@npub1xcl47srtwh4klqd892s6fzwtdfm4y03wzjfl78scmmmxg8wzsf4qftd0en](https://github.com/npub1xcl47srtwh4klqd892s6fzwtdfm4y03wzjfl78scmmmxg8wzsf4qftd0en)
- Lighter chat bubbles
- Date separators on chats
- Adds unfollow to note dropdown by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Improves (Show More) presence to only when it actually makes a difference.
- Adds UI for when the location permission is rejected.
- Improves error message for the signup page when the display name is blank
- Adds extra padding for the zap setup screen
- Pre-process search to avoid showing and hiding posts after hidden words were processed by the UI.
- Rotate on full screen video if the device orientation is not locked by [@npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k](https://github.com/npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k)

BugFixes:
- Fixes account creation that follows itself
- Fixes translations of http urls
- Fixes search bug that mixed geohashes and hashtags
- Fixes issue with the order of multiple same-author events in a thread
- Fixes drafts appearing for other logged in accounts by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Fixes jumping of scroll when the thread updates
- Fixes app hanging when switching to accounts due to waiting to decrypt the blocked user list that might not exist
- Fixes initial decryption of mutelists when using amber by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Fixes crash on empty p-tags on new replies [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Fixes translations preferences changes running on the main thread by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Fixes some images being saved as videos [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Fixes missing notifications when multiple accounts tried to decrypt it
- Fixes lack of zap progress updates when there is a private zap the user cannot decrypt or when a nwc payment fails.
- Fixes saving the scrolling position when navigating between multiple threads.
- Fixes rendering cache of notes in thread view which kept replies in the wrong thread level
- Fixes reply level calculation caching
- Fixes poll's background rendering with the right percentages
- Fixes "null" strings on profile fields.

Code Quality Improvements:
- Refactors FeedStructures to prepare for custom feeds
- Updates Account architecture to operate feeds with location
- Custom Empty Feed Screen for Notifications
- Fully Deprecates note1, removing the last usages of the standard
- Removes unused encryption fields from NIP-94
- Moves the parallel processing amber calls to a utils class
- Refactors location to operate as a flow
- Unifies location Flows and geoHash Flows into one
- Make location flows react to changing location permissions on the fly
- Moves NIP-44 test model classes to a new file
- Improves GitHub actions to prepare debug apks in every commit
- Upgrades to Coil 3
- Updates AGP, kotlin, runtime, compose, camera, corektx, media3, firebase, fragment, navigation, jna, jackson, accompanist, kotlin serialization, mockk, coroutines-test and kotlin collections

Performance:
- Improves thread preloading
- Adds a cache for reply levels when viewing threads.

Updated translations:
- Czech, German, Swedish and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Polish by [@npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm](https://github.com/npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm)
- Chinese by [@npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h](https://github.com/npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h)
- Slovenian by [@npub1qqqqqqz7nhdqz3uuwmzlflxt46lyu7zkuqhcapddhgz66c4ddynswreecw](https://github.com/npub1qqqqqqz7nhdqz3uuwmzlflxt46lyu7zkuqhcapddhgz66c4ddynswreecw)
- Thai by [@npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e](https://github.com/npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e)
- Bengali by [@npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t](https://github.com/npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t)
- Hindi by [@npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6](https://github.com/npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6)
- Spanish by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp) and [@npub1dnvslq0vvrs8d603suykc4harv94yglcxwna9sl2xu8grt2afm3qgfh0tp](https://github.com/npub1dnvslq0vvrs8d603suykc4harv94yglcxwna9sl2xu8grt2afm3qgfh0tp)
- Persian by [@npub1cpazafytvafazxkjn43zjfwtfzatfz508r54f6z6a3rf2ws8223qc3xxpk](https://github.com/npub1cpazafytvafazxkjn43zjfwtfzatfz508r54f6z6a3rf2ws8223qc3xxpk)


[Changes][v0.93.0]


<a id="v0.92.7"></a>
# [Release v0.92.7: Mute List fix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.92.7) - 2024-10-19

- [Fixes empty mute lists and mute words](https://github.com/vitorpamplona/amethyst/commit/aae2ffebd71d38917fd5f0df96ca2d1fc11c3ee1)

[Changes][v0.92.7]


<a id="v0.92.6"></a>
# [Release v0.92.6: More UI Fixes for Android 15](https://github.com/vitorpamplona/amethyst/releases/tag/v0.92.6) - 2024-10-17

- [If the Share target intent opens with the new post screen already active, just updates the post instead of creating a new one.](https://github.com/vitorpamplona/amethyst/commit/c0ba6f5b0071b59f06f63d0264ec9c4aaf2fcc79)
- [Fixes zapraiser progress bar](https://github.com/vitorpamplona/amethyst/commit/cff6ee457d02b6fe4d5a76f61ee9858bc7cea74f)
- [Fixes poll rendering](https://github.com/vitorpamplona/amethyst/commit/25c35326371678c8734691c9eb666cb091e6bd20)
- [Reduces the font size for the translation label](https://github.com/vitorpamplona/amethyst/commit/24b29396cb795bd18fdad92e8a2974e6479be159)
- [Fixes margin of poll options with translation](https://github.com/vitorpamplona/amethyst/commit/67fb3bf488cc24e1bf2bc4a1c74f9523d9540d05)
- [Fixes array too big because strfry blocks more than 20 filters](https://github.com/vitorpamplona/amethyst/commit/26ba75c7c97283a77453462018ebf5bfc7d37df2)

[Changes][v0.92.6]


<a id="v0.92.5"></a>
# [Release v0.92.5: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.92.5) - 2024-10-16

- [Updates compose, benchmark, navigation, and activity](https://github.com/vitorpamplona/amethyst/commit/724bea6bbb475bc074844c380834e0de200f300e)
- [Fixes recreating the new post screen from intent when switching accounts.](https://github.com/vitorpamplona/amethyst/commit/c5dd2c5651a24d2da7464cd4c683b4b7219e9963)
- [Prevent clicks outside boost popup](https://github.com/vitorpamplona/amethyst/commit/6826db13a4988bee32507500d138595dd3d596fa)

[Changes][v0.92.5]


<a id="v0.92.4"></a>
# [Release v0.92.4: Text and Media Share target for new posts.](https://github.com/vitorpamplona/amethyst/releases/tag/v0.92.4) - 2024-10-16

- [Makes Amethyst a share target for texts, images and videos.](https://github.com/vitorpamplona/amethyst/commit/13e2546d362794f20f104a1c424f78850054da67)
- [Fixes new media post dialog for the edge to edge borders](https://github.com/vitorpamplona/amethyst/commit/549b9f53e557e50a06f167b01432538bb02003b2)
- [Fixes padding of the new new post screen](https://github.com/vitorpamplona/amethyst/commit/974c022aedabe786aa57bab8871913c613230852)
- [Changes the new post screen to use the non-disappearing version of the scaffold.](https://github.com/vitorpamplona/amethyst/commit/a57566dd8455e69abe5f49b5ce9f9541bfa7e021)
- [Correctly maps the write status of the outbox relays.](https://github.com/vitorpamplona/amethyst/commit/b8112ff1aa7dd40cf437167803fc581173fb23bb)

[Changes][v0.92.4]


<a id="v0.92.3"></a>
# [Release v0.92.3: Shorts fling behavior bugfix.](https://github.com/vitorpamplona/amethyst/releases/tag/v0.92.3) - 2024-10-15

- [Uses the same Reaction animation to also do Boosts and speeds up animation](https://github.com/vitorpamplona/amethyst/commit/d7c18341cdf98e9def71df58a686188a243a2015)
- [Reverts hack for fling behavior on the Vertical Pager](https://github.com/vitorpamplona/amethyst/commit/d78b55d134f108251e0ec7b62b65ec25c68fc9a2)
- [Updates to AGP 8.7.1 and SDK 35](https://github.com/vitorpamplona/amethyst/commit/5b516f410156be4128d43b4e605fc2aaeeb7c948)
- [Do not bring the keyboard when the search field reappears.](https://github.com/vitorpamplona/amethyst/commit/1fc390c215d85308592372aa3228f1987186e748)
- [Fixes Thread view when loading a reply of a thread that is not cached yet.](https://github.com/vitorpamplona/amethyst/commit/2bb09f583ba1b9ff90c22c9c9fa221ae99435471)

[Changes][v0.92.3]


<a id="v0.92.2"></a>
# [Release v0.92.2](https://github.com/vitorpamplona/amethyst/releases/tag/v0.92.2) - 2024-10-11

- [Moves the API with amber from `signature` to `result`](https://github.com/vitorpamplona/amethyst/commit/5ef3c8f00ec06e156ab06c1c8605b2a5cefccae1)
- [Fixes the ability to see muted lists in Shorts](https://github.com/vitorpamplona/amethyst/commit/d832351bcd39a43d0b80fb2e00743732bbe5403c)
- [Adds new fields on vision prescriptions](https://github.com/vitorpamplona/amethyst/commit/9ae611831f4777732bc65e7f5ff9cbdda118980f)
- [Prioritise search results that start with the search term](https://github.com/vitorpamplona/amethyst/commit/99641e1a12b483ae2f0e66cf8563b510980eb732)
- [Adds some test cases for video compressions](https://github.com/vitorpamplona/amethyst/commit/76a66e7bd3b2bbcf2b51c56badfc5722aded40d7)
- [Adds Unknown media type test](https://github.com/vitorpamplona/amethyst/commit/a66417ec82040938805dd2b5cb3d7f9f114c78a8)
- [Use "use" blocks to close resources automatically](https://github.com/vitorpamplona/amethyst/commit/fbecc8b6d1585dce3d7d4688aef744e69cc2ea53)
- [Fixes long term issue with the video in the Shorts feed not aligning with the padding of the screen](https://github.com/vitorpamplona/amethyst/commit/d320b951ae02d536eaafeeb483763810fbcdda3d)
- [Fixes intent reopening the Zap Setup screen when coming back from Alby](https://github.com/vitorpamplona/amethyst/commit/c66fa1073bcdf1bd0ffd9084e288ac494c82b5ff)
- [Faster logout processing without closing the account switcher dialog.](https://github.com/vitorpamplona/amethyst/commit/67ea0dcf7fd791d970eaeb64b248d06ea2a587e3)
- [Add animation to notification chart](https://github.com/vitorpamplona/amethyst/commit/a3166f4ff3341e097271570ad68ef9bd4429e144)
- [Add animation to FABs](https://github.com/vitorpamplona/amethyst/commit/9340c440f5f45f68484dc9b6e4d82a27260d2e11)
- [Add animation to zap and reaction popups](https://github.com/vitorpamplona/amethyst/commit/a7343d5b5477ae16dd38f5c49cbfe042b8244b9d)
- [Updates AGP and compose, fragment, navigation, benchmarking and firebase libraries.](https://github.com/vitorpamplona/amethyst/commit/0551b82bd91b720cfc108def82215132f35c9782)
- [Prevent clicks outside reaction and zap popups](https://github.com/vitorpamplona/amethyst/pull/1125)
- [Support for login with hex key when using amber](https://github.com/vitorpamplona/amethyst/commit/baa9ee05626a645d1ea060e78e9231c2624bd666)
- [Video Rotation: actually rotate if the device orientation is not locked.](https://github.com/vitorpamplona/amethyst/commit/7326d6be93ffd8c197d42347ec2311368505ac52)

[Changes][v0.92.2]


<a id="v0.92.1"></a>
# [Release v0.92.1](https://github.com/vitorpamplona/amethyst/releases/tag/v0.92.1) - 2024-09-27

- Fixes Access to local Citrine when tor is enabled
- Fixes zap splits on a first time connection to the NWC relay.

[Changes][v0.92.1]


<a id="v0.92.0"></a>
# [Release v0.92.0: Tor and Transient Accounts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.92.0) - 2024-09-27

This version ships with a Tor service enabled by default to access .onion urls and untrusted relays from the Outbox model. It also offers NFC-hosted transient accounts: accounts that log off as soon as the app goes to the background, deleting all traces of the account from the phone.

Write your ncryptsec to an NFC tag and hide it in your clothing. When you need to use Amethyst, tap the tag, insert your password and login. Lock the screen to delete everything. If you are an activist and if your phone is confiscated, they will never find anything on the phone. Not even your public key. Ncryptsec is a NIP-49-based password-encrypted nsec. If you need, you can destroy and dispose the NFC tag.

Features:
- Adds tor node
- Adds multiple settings for the use of Tor
- Adds privacy presets to simplify Tor choices
- Adds support for NFC-hosted transient accounts
- Adds button to take and add pictures from camera by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Adds Uncompressed option when uploading media by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Adds support for Bloom filters
- Adds zapstore yaml setup
- Adds mempool api to verify OTS via Tor

Bug Fixes
- Fixes the zap options available for the Zap the Devs button
- Fixes edit draft not working when using the quick action menu by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Fixes opengraph url preview tags from substack by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Fixes the parsing of d-tags with colons in them
- Fixes back button not working after opening a nostr link from the web
- Fixes push notifications when using amber by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Fixes NIP-47 implementation to force relay for the NWC connection.
- Fixes OTS web calls via Tor using mempool's api
- Fixes the loading of localhost urls using Tor
- Fixes .onion urls not using tor even if available
- Fixes show buffering animation when loading videos
- Fixes folowing icon position on chat user pictures

Performance
- Starts to build all OkHttp clients from a main root client to keep the same thread pool
- Caches OTS web calls to avoid pinging the server repeatedly for the same event.

Code Quality Improvements:
- Updates navigation compose, lifecycle, fragment, activity, composeBoms and AGP to 8.6.1
- Improves OTS Verification error messages

Updated translations:
- Czech, German, Swedish and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Polish by [@npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm](https://github.com/npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm)
- Chinese by [@npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h](https://github.com/npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h)
- Slovenian by [@npub1qqqqqqz7nhdqz3uuwmzlflxt46lyu7zkuqhcapddhgz66c4ddynswreecw](https://github.com/npub1qqqqqqz7nhdqz3uuwmzlflxt46lyu7zkuqhcapddhgz66c4ddynswreecw)
- Thai by [@npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e](https://github.com/npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e)
- Bengali by [@npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t](https://github.com/npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t)
- Hindi by [@npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6](https://github.com/npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6)
- Spanish by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)

Download via [Obtainium](http://amethyst.social) or [Zap.Store](https://zap.store/)

[Changes][v0.92.0]


<a id="v0.91.0"></a>
# [Release v0.91.0 Edge to edge feeds](https://github.com/vitorpamplona/amethyst/releases/tag/v0.91.0) - 2024-09-06

Hidden words now filter by the user's fields as well. You can hide by name, profile picture, banner, lightning and nip-05 addresses and about me fields.

Features:
- Finishes Edge to Edge transition for Android 15
- Adds compression settings to the media uploading screen by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Adds sliding animations in all inner screens
- Adds copy stack to clipboard for error messages that have an exception
- Enables the use of hidden words for all visible properties of the user

Bug Fixes
- Fixes blank alt field when no alt text is provided on NIP-96
- Fixes missing Private Home/Outbox relay list after loading from backup
- Fixes keyboard padding issues when using physical keyboards on the message screens.
- Fixes token sanitization when using gcompatup with unified push by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Fixes moving top buttons on the full screen dialog for images and videos.
- Fixes weird padding of the key backup screen
- Fixes using npub instead of pubkey in hex when communicating with Amber
- Fixes blinking on crossfades when the system's light/dark theme is different than the app's theme
- Fixes a mix of languages after changing language in Settings
- Fixes disables saving m3u8 files locally (streaming can't be saved)
- Fixes Community tab not loading up with Global filter
- Fixes size of default banner when the profile is not loaded yet.

Code Quality Improvements:
- Inverts Layouts to place Navigation on top of Scaffold and allow custom scaffolds per route
- Refactors navigation to improve clarity
- Restructures screens into their own packages
- Restructures navigation functions as a single object
- Refactors all TopBars to use default material 3 ones
- Simplifies the "and 2 more" translations for the relay recommendation user lists
- Removes unnecessary observers from the transition in the bottom nav layouts.
- Normalizes cache directories in the Application class
- Fixes text field recompositions because of new keyboard actions
- Moves the profile zap to threads
- Updates to AGP 8.6.0
- Updates zoomable and kotlin serialization

Updated translations:
- Czech, German, Swedish and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Thai by [@npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e](https://github.com/npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)
- Bengali by [@npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t](https://github.com/npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t)
- Polish by [@npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm](https://github.com/npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)
- Chinese by [@npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h](https://github.com/npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h)

Download via [Obtainium](http://amethyst.social) or [Zap.Store](https://zap.store/)

[Changes][v0.91.0]


<a id="v0.90.6"></a>
# [Release v0.90.6: Zap Amount fix on Profile Zaps](https://github.com/vitorpamplona/amethyst/releases/tag/v0.90.6) - 2024-09-02

[Long press to copy relay url](https://github.com/vitorpamplona/amethyst/commit/53acbd860660dd1f1ba5098a8d1f2819e2806e4a)
[Rename amount to amountInMillisats to give visual clue to calling classes that millisats should be used](https://github.com/vitorpamplona/amethyst/commit/a4a753e7b623da776afeb47d293551c04ece994e)
[long press to copy relay url in the relay icons and relay row](https://github.com/vitorpamplona/amethyst/commit/bba7c257da6d6b978432358e0c738b2ab0455a2c)

[Changes][v0.90.6]


<a id="v0.90.5"></a>
# [Release v0.90.5: Orbot fix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.90.5) - 2024-09-01

- Fixes proxy connection not starting after killing the app

[Changes][v0.90.5]


<a id="v0.90.4"></a>
# [Release v0.90.4: Fixes relative urls on NIP-96 + Basic NIP46 objects](https://github.com/vitorpamplona/amethyst/releases/tag/v0.90.4) - 2024-08-30

[- Fixes caching bug of NIP-96 servers](https://github.com/vitorpamplona/amethyst/commit/aeaddf722bf42cc7140f07e36964dcd447a0a1e0)
-- Allow NIP-96 setups with relative URLs
-- Displays error messages if the server has sent in the body
-- Adds a test case for both
[Fixes ZapTheDevs card not disappearing after the donation.](https://github.com/vitorpamplona/amethyst/commit/3ebaf2bf1e49f75aa08b7fe047f18743f7389ff4)
[Fixes push registration with empty token ids](https://github.com/vitorpamplona/amethyst/commit/1407850e81b0ee58c32b9b5ad3be5ac5548555cc)
[Show relay ping with the relay icon](https://github.com/vitorpamplona/amethyst/commit/efde14a3483a467ac03fd08ec00111da36d36847)
[Enables decryption by nip04 and nip44 on NostrWalletConnect objects, NIP-51 lists and NIP-04 messages](https://github.com/vitorpamplona/amethyst/commit/cb4a73bb9c6138d960c543e9b54330be32a4b85f)
[Fix relay icons when using complete ui](https://github.com/vitorpamplona/amethyst/commit/06f37ab81d1428da37cf73b8b642faf2d5523326)
[Adds basic support for NIP-46 events.](https://github.com/vitorpamplona/amethyst/commit/b5709f95271e5b8fed25986da7f109dc8e2b8d43)
[Protects against empty nip04 content](https://github.com/vitorpamplona/amethyst/commit/8be74649ceda39a3bce8e9c32dbed0dabdcc3bb5)
[Basic code for dynamic limits](https://github.com/vitorpamplona/amethyst/commit/255f46404605136f6dd07d6bbf4f98c330683e33)
[Speeds up the filter for NWC zap payments.](https://github.com/vitorpamplona/amethyst/commit/388ccdbe750cba19d1ce579e72a29339d943d29f)
[Typo Fix on Lnaddress TextField](https://github.com/vitorpamplona/amethyst/commit/93e9e2373034c18cdcf6abc4968bb0322c93816d)

[Changes][v0.90.4]


<a id="v0.90.3"></a>
# [Release v0.90.3: Fixes Unified Push Notifications](https://github.com/vitorpamplona/amethyst/releases/tag/v0.90.3) - 2024-08-27

- Fixes Unified Push Notifications
- Fixes Lack of notifications for non-primary accounts
- [Adds a small border between the author and the message of a chat bubble](https://github.com/vitorpamplona/amethyst/commit/4665c488d5c340b68708b90d7416802ba0daac03)

[Changes][v0.90.3]


<a id="v0.90.2"></a>
# [Release v0.90.2: Fixes conversation feed.](https://github.com/vitorpamplona/amethyst/releases/tag/v0.90.2) - 2024-08-27



[Changes][v0.90.2]


<a id="v0.90.1"></a>
# [Release v0.90.1: Fixes error messages when zapping devs](https://github.com/vitorpamplona/amethyst/releases/tag/v0.90.1) - 2024-08-27



[Changes][v0.90.1]


<a id="v0.90.0"></a>
# [Release v0.90.0: Torrents and Outbox refactorings](https://github.com/vitorpamplona/amethyst/releases/tag/v0.90.0) - 2024-08-27

This version adds support for NIP-35 Torrent files (dtan.xyz), adds significant memory pruning for encrypted payloads and large events (DMs, Notifications, Zaps, etc), and completely restructures the way most of our caching works to facilitate immediate subscription updates using the outbox model on custom feeds by NIP-51 lists. This version also offers new defaults for NIP-65 and DM relay lists for new users and fixes several bugs and inconsistencies.

Features:
- Adds support for NIP-35 torrents and their comments
- Adds a simplified sync Signer to the Quartz library
- Adds Default lists for NIP-65 inbox and outbox relays
- Adds Default lists for Search relays
- Adds local backup for UserMetadata objects
- Adds local backup for Mute lists
- Adds local backup for NIP-65 relays
- Adds local backup for DM Relays
- Adds local backup for private home relays
- Improves caching of encrypted DMs
- Updates Twitter verification to X
- Improves the rendering of QR Codes
- Adds support to Delete All Drafts

Code Quality Improvements:
- Separates Account actions from Account state in two objects
- Changes Startup procedures to start with Account state and not the full account object
- Moves scope for flows in Account from an Application-wide scope to ViewModel scope
- Removes all LiveData objects from Account in favor of flows from the state object
- Migrates settings saving logic to flows
- Migrates PushNotification services to work without Account and only Account Settings.
- Migrates the spam filter from LiveData to Flows
- Rewrites state flows initializers to avoid inconsistent startups
- Finishes the migration of the service manager to the Application class
- Moves to hold the all feeds in stateflows
- Updates benchmark, composeBOM and firebaseBOM to the latest versions
- Moves the default zap type to a state flow and avoids passing on to the screen when using the default value
- Removing unecessary livedata objects for translation services
- Moves lastread routes to mutableStateFlow
- Migrating livedata to flow for contact list updates of the user.
- Adds a destroy method to FollowList state for consistency
- Moves follow list states to the AccountViewModel
- Migrates Notification Summary to the new state model
- Moves the notification screen to the new state model instead of viewModels
- Refactoring Moving feed status from ViewModel to State objects

Interface Improvements:
- Show only 3 users in the recommended relays section
- Creates links to njump when events can't be found on Amethyst
- Adds support for MOD reports
- Displays commitment PoW if present
- Changes relay set kind to be NIP-51 consistent.
- Adds more information to when error messages are not available in the relay stats.
- Adds context to highlight events
- Adds previews to test markdown rendering
- Improves the look of inlinde code in markdown
- Improves badge display

Performance Improvements:
- Adds pruning for giftwrapped messages
- Fixes clearing of flows and live data object pools before removing notes from the local cache
- Improves stability of composables
- Migrates caching of decrypted value outside of the Event class
- Removes encrypted parts of NIP-17 from the cache
- Removes old NIP-04 messages from the cache
- Avoids deleting new NIP-17 plain text chats from memory
- Avoids bottom nav recompositions
- Simplifies nav bar selected calculations
- Avoids remembering edit lists that will never exist.
- Improves speed of chatlist rendering

Bug Fixes:
- Fixes lingering cache and threads still active after killing the app
- Fixes crash when opening the Relay screen with empty urls as relays.
- Fixes horizontal padding of the chat messages
- Fixes the download of 1000s of NIP-65 relay lists because some relays consider empty lists as null and return everything.
- Fixes fdroid push registration to re-register even if the saved distributor was already selected.
- Fixes crash when the relay url of a user is duplicated.
- Fixes padding of short quotes
- Fixes slow down when the last message on chat is a base64 image.
- Fixes the centralization of the "and more" part of relay recommendations
- Fixes miscaching flows of the relay lists from follows.
- Fixes miscache of hashtag following button
- Fixes heading sizes on markdown
- Changes Delete all events to use maximum chunks of 200 elements to avoid the 65KB stringified JSON limit of many relays.

Updated translations:
- Czech, German, Swedish and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Polish by [@npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm](https://github.com/npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Arabic, Bengali by [@npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t](https://github.com/npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t)
- Thai by [@npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e](https://github.com/npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e) and [@npub1tr66yvqghfdgwv9yxhmg7xx6pvgvu5uvdc42tgdhsys8vvzdt8msev06fl](https://github.com/npub1tr66yvqghfdgwv9yxhmg7xx6pvgvu5uvdc42tgdhsys8vvzdt8msev06fl)
- Hindi by [@npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6](https://github.com/npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6)
- Spanish by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- Chinese by [@npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h](https://github.com/npub1gd8e0xfkylc7v8c5a6hkpj4gelwwcy99jt90lqjseqjj2t253s2s6ch58h) and [@npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7](https://github.com/npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)

Download via [Obtainium](http://amethyst.social) or [Zap.Store](https://zap.store/)

[Changes][v0.90.0]


<a id="v0.89.10"></a>
# [Release v0.89.10: Smaller memory footprint](https://github.com/vitorpamplona/amethyst/releases/tag/v0.89.10) - 2024-08-09

- [Improved filter for notifications](https://github.com/vitorpamplona/amethyst/commit/fad8b9df0fe28240da967db7b316d92050321e8d)
- [Moves service manager to the Application class](https://github.com/vitorpamplona/amethyst/commit/cc526816353a2919fc95bf459fba8570da2ff70a)
- [Adds protections against filters with empty arrays because some relays consider that null as opposed to empty.](https://github.com/vitorpamplona/amethyst/commit/939c8e95b0a130afaf7818ce601a2b4892f881ac)
- [Delete All Drafts now requires maximum chunks of 200 elements to avoid the 65KB stringified JSON limit of many relays.](https://github.com/vitorpamplona/amethyst/commit/89638ff261c7fccb9c409c49d6efb40fc1c4c922)
- [Updates translate dependencies](https://github.com/vitorpamplona/amethyst/commit/02f7b546613e8d371c2902611b92ba5c26e34c06)
- [Reducing the amount of CPU memory used for images to the default.](https://github.com/vitorpamplona/amethyst/commit/68cad97819d554f672a963cc61604f4a515fe3aa)
- [Improves wording on the name of relay types](https://github.com/vitorpamplona/amethyst/commit/2e7b7c5c000ef9a9ab95b7303d58981c24be68b0)
- [Marks username as deprecated](https://github.com/vitorpamplona/amethyst/commit/8ed78ea38fe1d02d21b33e9a350f21633d9891ff)
- [Adds zap amount cache for the memory space calculations](https://github.com/vitorpamplona/amethyst/commit/282d4614c0b000c82806105dd54c68a89ac4b4ec)
- [Allows users to select and copy the notice from the relay on the relay list dialog](https://github.com/vitorpamplona/amethyst/commit/7eaa553ebe1c12f413b0180f4bc13725146926b2)
- [Fixes the clickable regions to open additional relay info on the relay list dialog.](https://github.com/vitorpamplona/amethyst/commit/27d2e2309a1dbcb657e69c29d8ccc58666d42f40)
- [AGP to 8.5.2](https://github.com/vitorpamplona/amethyst/commit/312501e527a9ce126e9e346994e9ecc4c6e49914)

[Changes][v0.89.10]


<a id="v0.89.9"></a>
# [Release v0.89.9 Outbox model to filter large follow lists.](https://github.com/vitorpamplona/amethyst/releases/tag/v0.89.9) - 2024-08-07

- [Fixes the order of bookmarks (keeps the order of the event, instead of the created at)](https://github.com/vitorpamplona/amethyst/commit/ed0676a5f53e199fe85f4691f98946fd161fbded)
- [Improves the async rendering of Base64 content](https://github.com/vitorpamplona/amethyst/commit/f731c654b019e4a2f1395f44218e33d62f8e8759)
- [Moves discovery and video lists to Outbox when Follows or relay lists are selected](https://github.com/vitorpamplona/amethyst/commit/b026bffe4a57e8d53cc4b5385dc323caafcdb4bb)
- [Adds support for selecting authors based on their Outbox relays when searching for notes authored by them](https://github.com/vitorpamplona/amethyst/commit/a1aaec0b7d6cc14f5022462a54800e14a76ab396)
- [Aligns default note comparator to NIP-01's created at descending and then by id ascending](https://github.com/vitorpamplona/amethyst/commit/6f59097ac0612d3d236dee4f056e9a2ecb6259ec)
- [Keep them public to allow testing in these particular functions](https://github.com/vitorpamplona/amethyst/commit/e8574c10bbf357e221987c8dc94480ab972ceca3)
- [Refactors to use native contains instead of custom lambdas on Ammolite's Filter](https://github.com/vitorpamplona/amethyst/commit/7cb87ea9c5a115770c402656a10a176f75e1a3ac)
- [Refactors Ammolite Filters to be regular ones and creates a PerRelayFilter for the use on Amethyst.](https://github.com/vitorpamplona/amethyst/commit/07e5132943bf4c93337dd9e36223d7b95ed23669)
- [Renames the MinimumRelayList to RecommendationProcessor](https://github.com/vitorpamplona/amethyst/commit/2b1e3cfc93319c47bd66a728b47003e7aec1b060)
- [Adds haptic feedback to draft deletion swipe](https://github.com/vitorpamplona/amethyst/commit/e88d1d0d002430acf5641a23eb4f24377ec5e0df)
- [Moves the ContactList cache lists to AccountViewModel, where it can be disposed more efficiently.](https://github.com/vitorpamplona/amethyst/commit/5fdff97cf8e8e855e16dbaa0a1e60045887a1812)
- [Improves the accuracy of the Event memory counter.](https://github.com/vitorpamplona/amethyst/commit/e5328d79756dd45ea14605a4fbe606f47553d7e9)
- [Adds event factory performance test](https://github.com/vitorpamplona/amethyst/commit/71b45b96fbfbdcbbe2153812132723febc7d2924)
- [Adds extension possibility to Quartz's event factory](https://github.com/vitorpamplona/amethyst/commit/971c92b27a210c4cbebd1d1a5f23c7e06b0cd1c9)
- [Moves DataSource dispatcher from IO to Default](https://github.com/vitorpamplona/amethyst/commit/4c70065843af72d3963fd063bf3eee0e5d9f5f69)
- [Makes stringRes Stable for compose](https://github.com/vitorpamplona/amethyst/commit/ab772eb65e039f302b9e4d386ef99af2cd307540)
- [Removes Mutiny NWC button :(](https://github.com/vitorpamplona/amethyst/commit/84df86f4b6262870b837f927d60ab502093e4ae7)
- [Moves Relay viewModels to Default thread](https://github.com/vitorpamplona/amethyst/commit/a0289a0cb455846c177a039c6a663450d95b0dbd)

[Changes][v0.89.9]


<a id="v0.89.8"></a>
# [Release v0.89.8: Delete all drafts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.89.8) - 2024-08-02

- [Add delete all drafts button](https://github.com/vitorpamplona/amethyst/commit/609ccaa63579f3936af8f8d9a71310f5d78fd122)
- [Enables crossfading between image states](https://github.com/vitorpamplona/amethyst/commit/bca941e04595f2e853c8375ab07fa66b1127eae3)
- [Signs for just one auth event to register with the push notification service instead of the dozens of events, one per relay.](https://github.com/vitorpamplona/amethyst/commit/38ba456a223a5197caae1b78b561f91c9d53281f)
- [Adds the highlight quote to the base URL of a highlight event so that when the user opens the link, it highlights on the page.](https://github.com/vitorpamplona/amethyst/commit/b440661e0de387b44035b6728567c2da333ef504)
- [Fix order of Share ImageAction](https://github.com/vitorpamplona/amethyst/commit/cb02955ac601a862016f1db2d7ef7f0afc349364)
- [Fix copying to clipboard edited notes](https://github.com/vitorpamplona/amethyst/commit/25823b1f397f9269e64c8fd0f401fc42a834a30e)
- [Adds tests for `02` and `03` compressed keys to make sure they can encrypt and decrypt from and to each other.](https://github.com/vitorpamplona/amethyst/commit/143c3a14440ee164f09519a27522d1521464417a)
- [Updates depedencies](https://github.com/vitorpamplona/amethyst/commit/a03a11c4cc35473ee2a206b2887f5b8d13c9bedb)
- [Fix Flow Release if not subscribed anymore.](https://github.com/vitorpamplona/amethyst/commit/0258c5aac23bd285fba9a93c8bac2db44ff0a9a5)
- [Adds the mint information to each cashu preview](https://github.com/vitorpamplona/amethyst/commit/bfbcf11fa3a3e7dc0760febb0ed8b1490e8a3082)
- [Fixes Unable to add to media gallery after pressing Show More](https://github.com/vitorpamplona/amethyst/commit/231af1d3d84cc2fc8c2445d7974b9fc1da5ba8db) [#993](https://github.com/vitorpamplona/amethyst/issues/993)
- [Fixes duplicated parsing of content for uncited hashtags](https://github.com/vitorpamplona/amethyst/commit/2b2f04f724397a2f5bfe612a685c378333e0a39b)
- [Better rendering for live streams that are planned but already finished.](https://github.com/vitorpamplona/amethyst/commit/d1278a44771b3bf29ef359881b9b183a2758eee7)
- [Fixes the need for main thread note loading for galleries](https://github.com/vitorpamplona/amethyst/commit/0af0f745bf591818873d28137d2c0e1b77b9b2c1)
- [Simplifies the drawing of gallery entires without e tags.](https://github.com/vitorpamplona/amethyst/commit/3bbb780d2bacbe16a46206f4e468df59012568b5)
- [Adds support for CashuB tokens using CBOR](https://github.com/vitorpamplona/amethyst/commit/fc98442f8b6eb5af817f562f89997a27fa8a2871)
- [Minimizes relay querying when rotating status events for each user.](https://github.com/vitorpamplona/amethyst/commit/63e036a9bbae5de18002989a65f530cd2a72943c)
- [Fixes Client notifications of relay failures after marking them as closed.](https://github.com/vitorpamplona/amethyst/commit/fd06193e938653ef47e667856a9f95df06795d5a)

[Changes][v0.89.8]


<a id="v0.89.7"></a>
# [Release v0.89.7: minor fixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.89.7) - 2024-07-22

- Add sendAndWaitForResponse to Client.kt
- [Avoids recomposition of the entire line when changing user status.](https://github.com/vitorpamplona/amethyst/commit/231ca44e1cd5ccae95e6263fbea1a7d0665cde53)
- Debug: [Prints memory use in descending order by size](https://github.com/vitorpamplona/amethyst/commit/ca00c87a6e3ef597df99fe1be1ce844391cd766d)
- Fix: [updates the EOSE of users even if no user is found in the relay.](https://github.com/vitorpamplona/amethyst/commit/4def80c91433433e7146178340af4e94f1caac1c)
- Fix: Filter renewing [Sorts keys before making the filter.](https://github.com/vitorpamplona/amethyst/commit/3c1f57fac0bea8f2c351936d7c94ecae5d7a0b85)
- Fix: [Remove focus from status text field when closing the drawer](https://github.com/vitorpamplona/amethyst/commit/e166c36731fc94f1b653321a01497d53ac471b32)
- [updates coil and jackson libs](https://github.com/vitorpamplona/amethyst/commit/a636f4a786d745bce3e45a5bc390b428d92250c7)

[Changes][v0.89.7]


<a id="v0.89.6"></a>
# [Release v0.89.6: Fixes stringResource crash](https://github.com/vitorpamplona/amethyst/releases/tag/v0.89.6) - 2024-07-21

[Runs faster animations (200ms instead of 400ms)](https://github.com/vitorpamplona/amethyst/commit/52fef33662fd8e0e1e014b4359fab1790ee85e46)
[Adds the key to avoid applying to a separate navcontroller](https://github.com/vitorpamplona/amethyst/commit/2ef2f0d1a585f08e269194b5387b0c8aa7eb443c)
[Fixes stringResource cache](https://github.com/vitorpamplona/amethyst/commit/5a60c3a595869fc9b85ccc6655e3222b30cdfd19)

[Changes][v0.89.6]


<a id="v0.89.5"></a>
# [Release v0.89.5: Fixes the drawing of screen below the nav bar](https://github.com/vitorpamplona/amethyst/releases/tag/v0.89.5) - 2024-07-20

- [Fixes screen rendering below the 3-button navigation](https://github.com/vitorpamplona/amethyst/commit/8ed3f84cb77e637b8a8d832ae36925c08e12a34d)
- [increases disk cache to 1GB and adds a memory cache policy.](https://github.com/vitorpamplona/amethyst/commit/fa06aefbf19fab24f0124c84893d08becd5b9e7b)
- [Adds more memory stat dumping for production apps](https://github.com/vitorpamplona/amethyst/commit/b80008d695e27caf900c748ac82c72edf2430218)

[Changes][v0.89.5]


<a id="v0.89.4"></a>
# [Release v0.89.4: Migrates to edge to edge in preparation for Android 15](https://github.com/vitorpamplona/amethyst/releases/tag/v0.89.4) - 2024-07-19

- Fixes Darkmode Action bar crash
- Fixes empty relay list not connecting to anything (uses default relays instead)
- Fixes alt text of NIP-96 server lists
- Fixes auth infinite loop with nostr.wine
- Fixes the keyboard overriding parts of the screen
- Migrating to edge to edge in preparation for Android 15

[Changes][v0.89.4]


<a id="v0.89.2"></a>
# [Release v0.89.2: Bugfixes for TopNav Lists](https://github.com/vitorpamplona/amethyst/releases/tag/v0.89.2) - 2024-07-17



[Changes][v0.89.2]


<a id="v0.89.1"></a>
# [Release v0.89.1: Bugfix for invalid id crashes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.89.1) - 2024-07-16



[Changes][v0.89.1]


<a id="v0.89.0"></a>
# [Release v0.89.0: Profile Galleries, nip96 server setup and relay recommendations](https://github.com/vitorpamplona/amethyst/releases/tag/v0.89.0) - 2024-07-16

This version adds Profile Galleries, flexible NIP-96 image server settings and relay recommendations based on the outbox model. To add an image to your Gallery, click on the Share button and hit Add Media to Galery. This works on posts from you or from other people.

Features:
- Renders Base64 images and gifs by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Adds NIP-96 image server settings by [@npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k](https://github.com/npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k)
- Adds Profile Gallery by [@npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8](https://github.com/npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8)
- Adds outbox cache in order to resend events after relay authentication
- Force-updates relays that are sending old versions of replaceables or events that have been already deleted
- Adds follow-list based relay recommendations to the relay settings.
- Adds Malware Report type

Performance Improvements:
- Reduces interruptions to the main thread
- Adds performance monitors for framedrops in benchmark mode

BugFixes:
- Several improvements in the Push Notification API to never miss a Zap
- Fixes lack of text update when switching edited versions
- Fixes poll rendering behavior after deleting an option by [@npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k](https://github.com/npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k)
- Fixes discovery top nav list watcher staying active when the app goes to the background
- Fixes scoping issues with flattenMerge freezing Top Nav List updates
- Fixes Top Nav lists after a deletion event has been received
- Fixes a bug on clicking the user profile but loading the wrong one
- Fixes the post button disappeering when the Relay Settings top label is too large
- Fixes text cut off for very long posts by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Fixes double auth triggering NOTIFY from inbox.nostr.wine

Updated translations:
- Czech, German, Swedish and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Polish by [@npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm](https://github.com/npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Chinese by [@npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7](https://github.com/npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7)
- Arabic, Bengali by [@npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t](https://github.com/npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t)
- Thai by [@npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e](https://github.com/npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e) and [@npub1tr66yvqghfdgwv9yxhmg7xx6pvgvu5uvdc42tgdhsys8vvzdt8msev06fl](https://github.com/npub1tr66yvqghfdgwv9yxhmg7xx6pvgvu5uvdc42tgdhsys8vvzdt8msev06fl)
- Hindi by [@npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6](https://github.com/npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6)
- Spanish by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)

Code Quality Improvements:
- Removes decryption for DVM responses since it doesn't encrypt statuses
- Upgrades lifecycle to 2.8.3, Kotlin to 2.0.0, Lint to 1.3.1 and AGP to 8.5.1
- Migrates sdk requirements to the version catalog
- Removes unnecessary dependencies for Ammolite
- Fixes several memory issues on CI due to Kotlin 2.0
- Removes the bugfix for reproducible builds since it has been fixed
- Solves build slowdown on spotless dependencies


Download via [Obtainium](http://amethyst.social) or [Zap.Store](https://zap.store/)

[Changes][v0.89.0]


<a id="v0.88.7"></a>
# [Release v0.88.7: Solves Push Error when using 2+ accounts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.88.7) - 2024-06-29



[Changes][v0.88.7]


<a id="v0.88.6"></a>
# [Release v0.88.6: Fixes double auth triggering NOTIFY from inbox.nostr.wine](https://github.com/vitorpamplona/amethyst/releases/tag/v0.88.6) - 2024-06-28



[Changes][v0.88.6]


<a id="v0.88.5"></a>
# [Release v0.88.5 Swipe-to-Delete Drafts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.88.5) - 2024-06-28

#Amethyst v0.88.5: Swipe-to-Delete Drafts

Features:
- Creates Ammolite, a library to host Relay access for other Nostr Clients by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Adds author picture when writing posts and replies.
- Adds a Swipe to delete action on the Drafts screen.
- Moves to non-deterministic signatures
- Renders relay lists (NIP-65, NIP-17, and Search kinds) as notes in the feed.
- Adds auth.nostr1.com as a recommendation for private inbox relays
- Adds uploading error messages for common HTTP status codes when uploading images/videos

Performance Improvements:
- Saves a copy of the NIP65 and NIP17 relay lists locally

BugFixes:
- Fixes not showing relay icons when sending chat messages with Amber.
- Adjusts the size of the reply button on chats
- Fixes the rendering of highlights when no user is present and includes options to render by e tags
- Fixes the position of the hash verification icon on NIP-95 images.
- Avoids using SSL on localhost relays by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Fixes alignment of the close button on Chat's reply preview
- Adjusts default zap amounts for the Zap the Devs button
- Fixes decryption error on the PrivateOutboxRelayList event for an account that is not currently active.
- Fixes extra } when rendering hashtags from Markdown
- Fixes empty filters when the logged-in accounts only include the current account.
- Fixes bug on string resources showing the same Zap amount and message for different Zap notifications.

Updated translations:
- Polish by [@npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm](https://github.com/npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Chinese by [@npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7](https://github.com/npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7)
- Arabic, Bengali by [@npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t](https://github.com/npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t)

Code Quality Improvements:
- Logs an error message in the Relay Dialog when the relay does not accept a new event
- Updates dependencies
- Changes "app" directory to "amethyst" due to the amount of libraries we now have in the same repo
- Renames JsonFilter to just Filter and adds a matching function
- Rearranges Quartz's crypto package into separate nips and reduces the amount of circular dependencies.
- Removes old highlight rendering functions
- Refactors PlaybackService

Download via [Obtainium](http://amethyst.social) or [Zap.Store](https://zap.store/)

[Changes][v0.88.5]


<a id="v0.88.4"></a>
# [Release v0.88.4: Bigger reaction panel](https://github.com/vitorpamplona/amethyst/releases/tag/v0.88.4) - 2024-06-22



[Changes][v0.88.4]


<a id="v0.88.3"></a>
# [Release v0.88.3: New Reactions Popup](https://github.com/vitorpamplona/amethyst/releases/tag/v0.88.3) - 2024-06-21



[Changes][v0.88.3]


<a id="v0.88.2"></a>
# [Release v0.88.2: Reverting old account structure and migration](https://github.com/vitorpamplona/amethyst/releases/tag/v0.88.2) - 2024-06-20



[Changes][v0.88.2]


<a id="v0.88.1"></a>
# [Release v0.88.1: Solves Inbox.Nostr.wine notification](https://github.com/vitorpamplona/amethyst/releases/tag/v0.88.1) - 2024-06-20



[Changes][v0.88.1]


<a id="v0.88.0"></a>
# [Release v0.88.0: Performance Mode](https://github.com/vitorpamplona/amethyst/releases/tag/v0.88.0) - 2024-06-19

#Amethyst v0.88.0: Performance Mode and Performance improvements

This version adds several performance improvements and includes a new UI mode in Settings that is designed for older phones. On that mode, all CPU-based animations are disabled, the use of transparency is minimized and the individually-generated robots are replaced by a static image. We also recommend disabling the Immersive Scrolling when using older phones.

Features:
- Adds performance mode on Settings
- Adds login with NIP-05 address
- Adds outbox relays to zap request: sender, receiver and author relays.
- Adds the NIP-65 relay to zap split tag instead of kind3 relays.
- Adds support for AVIF images
- Adds flare.pub videos to the media tab
- Replaces the post view count for a Share icon in the main feed.

Performance Improvements:
- Centralizes stringResource calls to cache them and avoid disk use
- Removes several unecessary UI states created during rendering
- Moves uncited hashtag parsing to a thread
- Replaces InputButton for ClickableBox to avoid loading colors during rendering
- Switches Social Icons mip-mapped PNGs to faster SVG versions
- Faster calculation of uncited hashtags in content
- Improves the speed of Robohash rendering
- Moves chatroom user group away from immutable sets
- Speeds up long-press Quick Action menus
- Optimizes NIP-11 fetch and avoids requesting twice in the same minute
- Redesigns the UI Components of the relay icons better performance
- Creates a relay flow cache to speed up the relay layout
- Combines hidden and reporting flows in a new cache
- Removes post reports Live data
- Refactors Full Bleed design of the master note
- Unifies Hidden and Report checks between the Video Feed, the Full Bleed Design and the Card layout.
- Adds a hashcode cache to speed up O(1) requests of spam and blocked user public keys
- Minimizes memory alloc by adding a native forEach and Map functions in the event's tag array
- Reduces double launch of co-routines
- Improves memory use of updates to the subscription after EOSE events
- Simplifies intrinsic size calculations for Image Previews and Videos
- Improves rendering time of chat messages

BugFixes:
- Fixes the inconsistency of button animation size in the reaction row
- Fixes the inconsistency of the Zap button graphics
- Fixes the Giftwraps query by EOSE date mismatch
- Fixes the keyboard's Go Button action on new user screen
- Stops redirecting when resolving nip05 addresses by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Fix hidden notes when hidden words is empty by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Fixes Url Preview crop when the image is too small or to big for the preview card.
- Better error messages when NIP-11 queries fail
- Fixes use navigateUp instead of popBackStack to avoid closing the app on double clicks on the back button
- Fixes the centered url on videos without active playback
- Fixes the padding of the reaction row on quotes
- Solves notification dots appearing on the navigation bar due to a hidden post
- Increases the download limit for reactions/zaps to events from 100 to 1000
- Fixes zap split rounding precision
- Fixes padding of the zap raiser
- Avoids showing error message if devs have removed their lnadress on the Zap the Devs card.
- Fixes padding and border of the zap split section on the master note
- Removes gray border in image urls that couldn't be loaded.
- Fixes alignment of reactions
- Fixes not centered Blank Notes
- Fixes scrollable drawer for all screen sizes
- Fixes search limits for profiles from 100 to 1000 events
- Re-normalizes all relays urls before connecting to reduce duplications
- Fixes the jittering from resizing Videos and Images during loading.
- Fixes landscape video centralization
- Forces relay URLs to be single line.

Updated translations:
- Czech, German, Swedish and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Hindi by [@npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6](https://github.com/npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6)
- Polish by [@npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm](https://github.com/npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Spanish by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)
- Chinese by [@npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7](https://github.com/npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)

Code Quality Improvements:
- Removes old Robohash bitmap-based generation
- Upgrades Compose, Lifecycle and Fragments

Download via [Obtainium](http://amethyst.social) or [Zap.Store](https://zap.store/)

[Changes][v0.88.0]


<a id="v0.87.7"></a>
# [Release v0.87.7: Revert Save button](https://github.com/vitorpamplona/amethyst/releases/tag/v0.87.7) - 2024-06-11

- Reverts Image dialogs to have a separate save button
- Adds save button to video player.
- [Fixes not centered Blank Notes](https://github.com/vitorpamplona/amethyst/commit/165c3bb852ece90a5fcad676f6710fcd5b356097)
- [Fixes scrollable drawer for all screen sizes](https://github.com/vitorpamplona/amethyst/commit/2e9683491fce9197245f4aef0c1dd6f5534d2bfd)

[Changes][v0.87.7]


<a id="v0.87.6"></a>
# [Release v0.87.6: AOSP-based keyboard Fix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.87.6) - 2024-06-08

- Fixes relay drops and difficulty in connecting when switching WIFI to Mobile
- Fixes the scrolling of the followed hashtags feed on User Profile
- Fixes crashing when tagging users with old AOSP-based keyboards
- Adds query by hashtag in reposted notes
- Fixes inbox.nostr.wine Pay Notification when already paid
- Performance: Moves OTS attestations local cache to a flow instead of live data
- Restructures video and image views and dialogs to avoid some buttons getting on top of one another
- Fixes videos re-starting from 0 when clicking in the Playing Video notification card
- Performance: Moves the video release to a threadsafe procedure
- Fixes fiatjaf search: Adds larger search limits for profiles

[Changes][v0.87.6]


<a id="v0.87.5"></a>
# [Release v0.87.5: Re-normalizes all relays](https://github.com/vitorpamplona/amethyst/releases/tag/v0.87.5) - 2024-06-04

- Re-normalizes all relays urls before connecting to reduce duplications ([`d10b4c6bde`](https://github.com/vitorpamplona/amethyst/commit/d10b4c6bdee0f57f6f855b9ec9032059e08d89ba))
- Normalizes all new relays in the edit screens.
- [Adds flare.pub videos to the media tab on Amethyst](https://github.com/vitorpamplona/amethyst/commit/f1e516662c53c2f72bbed087e0ede168a28ee841)
- Performance Improvements: Simplifies intrinsic size calculations for Image Previews and Videos
- Improves rendering of chat messages
- Fixes the jittering from resizing Videos and Images during loading.
- Fixes video cut off when loading
- Fixes blurhash covering videos in landscape
- Fixes landscape video centralization
- Fixes chat using Two Pane layout for landscape and regular for portrait
- [Improving the detection of image types on the Media Feed.](https://github.com/vitorpamplona/amethyst/commit/1bf81656417ce41e619155394f27ec930fade859)

[Changes][v0.87.5]


<a id="v0.87.4"></a>
# [Release v0.87.4: Fixes Live Streaming getting stuck](https://github.com/vitorpamplona/amethyst/releases/tag/v0.87.4) - 2024-06-03



[Changes][v0.87.4]


<a id="v0.87.3"></a>
# [Release v0.87.3: Fixes for using the wrong relays for DVMs.](https://github.com/vitorpamplona/amethyst/releases/tag/v0.87.3) - 2024-06-03



[Changes][v0.87.3]


<a id="v0.87.2"></a>
# [Release v0.87.2: Relay connection bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.87.2) - 2024-06-01



[Changes][v0.87.2]


<a id="v0.87.1"></a>
# [Release v0.87.1: BugFix missing notification dot](https://github.com/vitorpamplona/amethyst/releases/tag/v0.87.1) - 2024-06-01



[Changes][v0.87.1]


<a id="v0.87.0"></a>
# [Release v0.87.0: DVMs and Gossip](https://github.com/vitorpamplona/amethyst/releases/tag/v0.87.0) - 2024-06-01

#Amethyst v0.87.0: Data Vending Machines (DVMs) and Gossip Model

This version adds support for Data Vending Machines for content discovery. You can request a job by simply navigating to the 4th tab and choosing one of the DVMs. It also starts our support for the Inbox/Gossip model. The relay setup screen has been rewritten to support the many types of relays Amethyst will start to use. Please add your relays as you see fit.

Our video caching system has been improved. Most of the high-bandwidth use of the app in the last month has been due to a faulty caching system for some video types. The app was just downloading them at every playback. In a similar way, the caching system for encrypted events has been massively improved. The app should feel visibly lighter at this point.

If you generated your keys from NIP-06 seed words you can now type them on the login screen to start the app. We are not generating seeds yet, but this will be available in the upcoming versions.

We are also moving to deprecate and remove most of the NIP-04 usage in the app. In the future, users won't be able to send new NIP-04 DMs but the history of past DMs will be available for as long as we can support it.

Features:
- Adds support for NIP-90, data vending machines by [@npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8](https://github.com/npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8)
- Adds support for discovery content DVMs in the discovery tab by [@npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8](https://github.com/npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8)
- Adds support for paid DVMs by [@npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8](https://github.com/npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8)
- Adds support for NIP-06 seed word key derivation (bip32 and bip39) when logging in
- Adds support for NIP-65 relay lists
- Adds support for NIP-17 private DM relay lists
- Adds support for private relay lists to save Draft events
- Adds support for local relays as a separate relay set, saving locally only.
- Adds message + dialog to setup Search relays when searching
- Adds message + dialog to setup DM relays when messaging
- Adds signString method for Amber by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Prefers NIP-65 relays for zap request relay tags
- Prepares for NIP-96 server list integration
- Adds paste from clipboard button to NWC screen by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Moves video compressing quality to medium instead of low
- Adds recommended amounts for the Zap the Devs
- Reduces default zap amounts due to the change of Bitcoin price
- Allows the new post's relay selection dialog to pick any relays (instead of just writing relays) by [@npub10npj3gydmv40m70ehemmal6vsdyfl7tewgvz043g54p0x23y0s8qzztl5h](https://github.com/npub10npj3gydmv40m70ehemmal6vsdyfl7tewgvz043g54p0x23y0s8qzztl5h)
- Improves Zap efficiency for large zap splits
- Adds a queue of commands while the relay connects
- Makes sure only one connection per URL is made when doing splits
- Removes unnecessary Amber calls when decrypting private zaps
- Improves Zap error messages to include the lnaddress of the error
- Displays Zap Split error messages in sequence instead of in multiple popups.

Bug Fixes:
- Waits 500ms before restarting all connections when saving new relays
- Automatically deactivate writes for search relays (they are read-only)
- Overrides pubkey to avoid impersonators on seals.
- Fixes the rendering of replies on wikipages.
- Fixes duplicated imeta tags when uploading the same image twice
- Removes reposts from the Dot Notification in the home's bottom bar icon
- Fixes a white space when including an image url after a new line
- Fixes alignment of the like icons after the like event
- Fixes wrong display of original and forked notes
- Improves the rendering of Channels and Communities when quoted
- Slightly better rendering Drafts in the thread
- DecimalFormats are not thread safe, moving them to thread objects
- Block error messages from closing the Zap split payment screen
- Better formats zap amounts (don't show .0 if the previous numbers are large)
- Fixes the offset position of the payment amounts on the Zap the Devs message
- Fixes Copy Text function of DraftEvents
- Fixes top bar lists not updating when following communities and hashtags.
- Show toast error if unable to hide words by [@npub10ug9xs24ay5339agakaqk556t6zvq9qn5vm0vlhc4pu25cx0l32qxhrm9e](https://github.com/npub10ug9xs24ay5339agakaqk556t6zvq9qn5vm0vlhc4pu25cx0l32qxhrm9e)
- Adds Autofocus when entering the search screen by [@npub10ug9xs24ay5339agakaqk556t6zvq9qn5vm0vlhc4pu25cx0l32qxhrm9e](https://github.com/npub10ug9xs24ay5339agakaqk556t6zvq9qn5vm0vlhc4pu25cx0l32qxhrm9e)
- Fixes the use of Global-active relays in the Global Feed
- Fixes special chars on URL previews by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Fixes the lack of refresh when adding hidden words in the Security filters
- Makes sure only one buffering action is run for each video view
- Increases timeout to Tor connections
- Fixes a bug with `signature-null` in the sig of events from Amber

Updated translations:
- Czech, German, Swedish and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Polish by [@npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm](https://github.com/npub16gjyljum0ksrrm28zzvejydgxwfm7xse98zwc4hlgq8epxeuggushqwyrm)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)
- Swahili by [@npub1q6ps7m94jfdastx2tx76sj8sq4nxdhlsgmzns2tr4xt6ydx6grzspm0kxr](https://github.com/npub1q6ps7m94jfdastx2tx76sj8sq4nxdhlsgmzns2tr4xt6ydx6grzspm0kxr)
- Thai by [@npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e](https://github.com/npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e)
- Hindi by [@npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6](https://github.com/npub1ww6huwu3xye6r05n3qkjeq62wds5pq0jswhl7uc59lchc0n0ns4sdtw5e6)
- Spanish by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)

Performance Improvements:
- Optimizes Blurhash generation (4x gains)
- Speeds up the URL finder in the new post edit text (8x gains).
- Reduces the use of background colors to improve rendering speeds (15% gains).
- Refactors Giftwrap caching to delete encrypted text and reload the wrap if necessary (20x savings in memory use).
- Changes message wrap host to a host stub to reduce memory use
- Only download GiftWraps form 2 days past the last EOSE
- Moves the thread formatter and calculator out of Note to avoid memory use
- Slight improvement on the thread view for badges
- Unifies NIP01 Serialization with SHA-256 procedures to reduce the creation of several byte arrays at every verification
- Minimizes costs of keeping track of the number of events received per subscription
- Moves ClientController executor to a thread
- Speeds up ID calculations for Amber's Intent call

Code Quality Improvements:
- Major refactoring of the Relay List screens
- Refactors Relay URL formatter to Quartz
- Adds new observer structure for the LocalCache
- Moves Blurhash code to the commons module
- Updates UnifiedPush by [@npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k](https://github.com/npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k)
- Migrates to the latest Kotlin, Compose, and AGP 8.4.1 and several other dependencies

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.87.0/amethyst-googleplay-universal-v0.87.0.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.87.0/amethyst-fdroid-universal-v0.87.0.apk )

[Changes][v0.87.0]


<a id="v0.86.5"></a>
# [Release v0.86.5](https://github.com/vitorpamplona/amethyst/releases/tag/v0.86.5) - 2024-04-11

[Enables Mutiny Wallet NWC](https://github.com/vitorpamplona/amethyst/commit/b90a57220d11e84f068641896bdaf5da941102ea)
[Removes the use of DM relays to find events due to private inbox settings](https://github.com/vitorpamplona/amethyst/commit/a538b66db328229931fd513269f4c323a27b64fe)
[Adds vertical scrolling on the Zap page for collaborators.](https://github.com/vitorpamplona/amethyst/commit/f04631b0dd74df82f4204276ad99d85068b50223)
[Avoids decrypting existing Nostr events just to add the relay into the relay list.](https://github.com/vitorpamplona/amethyst/commit/6e31cff99c179c52cfe586ee64061691035f2850)
[Calculates hash in the IO thread from Compose's scope.](https://github.com/vitorpamplona/amethyst/commit/1553640c1846979a9100c2507e95f36a623b7f7e)
[New Crowdin translations by GitHub Action](https://github.com/vitorpamplona/amethyst/commit/f6cce42028a834037eb1e5cf1538d88c746e6cc4)

[Changes][v0.86.5]


<a id="v0.86.4"></a>
# [Release v0.86.4: Start up bug fixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.86.4) - 2024-04-11

[Updates AGP](https://github.com/vitorpamplona/amethyst/commit/fa7aa3cf241fcc993cf864b57dbbf840d2e9fbbe)
[Updates compose BOM](https://github.com/vitorpamplona/amethyst/commit/d38b57025cd6a2ff3c3d493e7e5e7f80b1cb9db2)
[Updates Kotlin version](https://github.com/vitorpamplona/amethyst/commit/eca5b47ab0a1a302cf4ca29bdbdec802ae0fa2fd)
[Fixes missing Zaps and some DMs on startup](https://github.com/vitorpamplona/amethyst/commit/4722b2a6172a8d706d0f8aafc9af6344520a8608)
[Do not use tor proxy when localhost, fix proxy not being used inside ImageDownloader.kt](https://github.com/vitorpamplona/amethyst/commit/31516964c86e51f2840864314a2a9255680ea376)
[Fixes image downloader not going through Orbot's Proxy](https://github.com/vitorpamplona/amethyst/commit/c4250ccd352d0f059b70e879fb125803142abd2c)

[Changes][v0.86.4]


<a id="v0.86.3"></a>
# [Release v0.86.3: New Markdown Parser](https://github.com/vitorpamplona/amethyst/releases/tag/v0.86.3) - 2024-04-10

- Migrates to the new, faster Markdown Parser
- Adds Note previews on Markdown
- Adds Custom hashtag icons to Markdown
- Adds URL preview boxes to Markdown
- Fixes Missing notifications
- Fixes clickable route not showing the user's npub before loading their name
- Fixes max width of hidden notes making them off-centered
- Moves parsing and saving an embed event to the IO thread
- Improves the secondary button design of the encrypted nsec backup page

[Changes][v0.86.3]


<a id="v0.86.2"></a>
# [Release v0.86.2: Fixes Draft Deletion in Threads bug](https://github.com/vitorpamplona/amethyst/releases/tag/v0.86.2) - 2024-04-05

#Amethyst v0.86.2: Draft BugFixes

- Fixes Draft Event not being deleted in threads
- Fixes draft decryption error
- Adds ws:// if not present in .onion relay urls
- Fixes double notifications

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.86.2/amethyst-googleplay-universal-v0.86.2.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.86.2/amethyst-fdroid-universal-v0.86.2.apk )

[Changes][v0.86.2]


<a id="v0.86.1"></a>
# [Release v0.86.1: Fixes Draft into Draft infinite loop](https://github.com/vitorpamplona/amethyst/releases/tag/v0.86.1) - 2024-04-04



[Changes][v0.86.1]


<a id="v0.86.0"></a>
# [Release v0.86.0 Draft Support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.86.0) - 2024-04-04

This version adds support for draft notes autosaved on your relays, a new simplified UI choice on Settings, changes the Discover tab algorithm to show the latest of chats and communities and much more.

Features:
- Draft notes for feeds, replies, live streams, public chats, NIP-04 DMs, GiftWrap DMs, polls and classifieds by nostr:npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Adds autosave for Drafts by nostr:npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Adds edit draft in the dropdown menu and the long press popup by nostr:npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Adds a Draft feed screen for all posts
- Adds new algorithm to parse OpenGraph tags by nostr:npub168ghgug469n4r2tuyw05dmqhqv5jcwm7nxytn67afmz8qkc4a4zqsu2dlc
- Adds a Simplified UI setting to both feeds and chats
- Moves the username play button to the profile page.
- Adds link to the version notes when clicking in the version in the drawer
- Brings new git Issues and Patches to the Notification
- Filters out too many reposts of the same note when on the main feed
- Updates the bootstrap relay list
- Adds missing classes to support WebServer connections in the Video Playback
- Slightly reduces line height for improved readability
- Reduces the space between chat bubbles.
- Migrates shareable links from habla.news to njump.me
- Restructures the Discover Tab to show the Chats and Communities with the most recent new content.
- Adds a bot field to the user info
- Adds k-tag to the Deletion events
- Adds button to load Zap Splits from the cited users in the text
- Several accessibility improvements by nostr:npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef

Bug Fixes:
- Fixes the post cut-off when the post has massive string chars without spaces or new lines
- Fixes missing Fhir Classes on Release
- Don't show the button to edit the post if the author of the original post is not the logged in user
- Fixes crash parsing multiple results from Amber by nostr:npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5
- Fixes the load of edits on a new edit proposal
- Fixes forking information kept from a previous post
- Fixes search on binary content
- Fixes space after clickable user display
- Centers Blank Note when post was hidden by the user.
- Accepts JSON events with escaping errors
- Fixes the parsing of user metadata events several times due to large coroutine backlog
- Fixes Scheduled Tag in LiveStreams
- Fixes the isOnlineCheck for nostr nests.
- Fixes sorting contract issues when follow status and user names are the same between keys
- Fixes tickmarks on dropdowns
- Checks if a Classified is wellformed before rendering
- Fixes size and alignment of the text when the live stream video is not present.
- Fixes some imports for benchmarks
- Fixes infinite Quotation issue (3 quotes are allowed).
- Fixes crashing with too many videos in quoted posts.
- Fixes double Show More when the user has hidden a post and ALSO the user's followers have reported the post.
- Only shows OTS to the respective Edit
- Fixes a bug with the latest version of jackson
- Avoids showing machine-code errors when paying for zaps on external wallets
- Fixes too strict timing constraints for new posts (accepts up to a minute in the future)
- Fixes following of geotags
- Fixes lack of zap amount refresh after zapping a note.
- Fixes videos not being able to seekTo the zero position.
- Fixes layout issues of Blog Post summaries when images are not present.
- Doesn't show edits of blog posts in the User's Profile
- Fixes Amber's deep sleep: Adds a lifeCycleOwner listener to register external signers on resume
- Fixes missing context in some replies to blog posts.
- Adds a space after the Channel header in the reply rows
- Centralizes the counter after the list of participants in a live event.
- Fixes double mention to Community headers when seeing a reply to a community post.
- Fixes Chat preview images when no image has been set.
- Fixes the display or Zap Events when All Follows is selected in Notifications
- Fixes the reply event finder for the reply row of text notes
- Makes hidden cards full width on the discovery feed
- Fixes the width of muted messages on chat feeds.
- Fixes the feed updates after list selections on the Discovery pages.
- Realigns the reaction icons and texts between main feed and video feed.
- Fixes garbled URL preview for non-UTF-8 HTML by nostr:npub168ghgug469n4r2tuyw05dmqhqv5jcwm7nxytn67afmz8qkc4a4zqsu2dlc
- Adjusts icon sizes on the galleries
- Avoids publishing with two equal `t` hashtags when the user already writes them in lowercase
- Limits the size of image previews from opengraph from being too big
- Fixes NPE with the cached state.
- Increases the push notification max delay to 15 minutes
- Fixes controller comparison for keep playing
- Fixes tag markers for replies in DMs
- Fixes layout of the reply row in chats
- Fixes lack of blurhash preview in some videos
- Fixes the lack of following mark on user pictures in chats
- Fixes the UI spacing for channels
- Fixes the use of filters that didn't discriminate the relay type setup
- Holds the state of Show More when switching edits and translations
- Renders DMs and live chats in the feed if they show up there
- Fixes contract violation when sorting users

Updated translations:
- Spanish by nostr:npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903
- French by nostr:npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz
- Dutch by nostr:npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd
- Swahili by nostr:npub1q6ps7m94jfdastx2tx76sj8sq4nxdhlsgmzns2tr4xt6ydx6grzspm0kxr
- Chinese by nostr:npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7
- Bengali by nostr:npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t
- Hungarian by nostr:npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp
- Czech, German, Swedish and Portuguese by nostr:npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef
- Arabic by nostr:npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t
- Thai by nostr:npub1tr66yvqghfdgwv9yxhmg7xx6pvgvu5uvdc42tgdhsys8vvzdt8msev06fl and nostr:npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e

Performance Improvements:
- Switches Robohash to Precompiled SVGs in order to reduce the memory burned of creating Strings with SVGs on the fly
- Restructures Data Access filters and LocalCache to use a ConcurrentSkipList instead of ConcurrentHashMap
- Only download video, image and audio files in NIP-94
- Updated hashtag icons for performance
- Avoids checks if a filter has changed before generating JSon strings
- Cleans up User Metadata upon receipt instead of in every rendering
- Simpler/Faster UserDisplay renderings
- Reduces video cache from 10 to 4 videos
- Removes coroutine use for Hashtags
- Brings the ZapForwarding icon to Compose
- Simplifies the algorithm to check if chatroom sender intersects with the follow list
- Avoids serializing temporary fields on Quartz
- Refactors views to the video and chat feeds
- Restructures NoteCompose for performance
- Restructures markAsRead to minimize threading cost
- Adds a large benchmark test for duplicate events
- Optimizing the performance of Highlight rendering
- After memory cleanup, only trigger liveData when it actually changes
- Minimizes memory use to calculate zaps
- Avoids triggering an error when decoding invalid hexes
- Reduces the amount of co-routines being launched in each LiveData update
- Migrates channel list and channel notes to LargeCache
- Removes the use of data classes to speed up comparisons
- Improves Nostr filter to bring public chat messages and avoid public chat creation and metadata updates
- Removes jsoup from dependencies
- Removes the need to observe author changes to event after loaded
- Turns hashtag icons into programmable vectors
- Moves the Following Vector to a native composable
- Removes unnecessary modifier layouts from the top bar
- Uses the cached thread pool instead of the scheduled thread pool for translation services
- Avoids launching coroutines that were just launched
- Makes a cache for Media Items
- Only changes the keep playing status if different
- Reduces recompositions after video/image hash verification
- Avoiding feed jitter when pressing the notification button twice

Code Quality Improvements:
- Breaks massive NoteCompose down into each event type
- Removes the release drafter plugin on actions. Too buggy
- Removes dependency of the Robohash from CryptoUtils
- Improves Preview helper classes
- Updates secp256k1KmpJniAndroid, compose, zoomable, media3, jackson and firebase libs
- Updates AGP to 8.3.1
- Deletes the old Settings local db
- Refactors some of the old display name structure
- Refactors Relay classes.
- Isolates the LargeCache forEach method to allow quick time measurements on filters
- Reorganizes classes in the commons lib
- Fixes test cases for nip96 uploaders
- Removes unused addMargin param
- Refactoring caching systems for the Compose layer
- Aligns the BOM between implementation and tests
- Refactors the use of dividers out of components
- Refactors composables to load events, check hidden and check report
- Fix Kotlin encryptDecryptTest to decrypt with swapped private and public keys to follow NIP-44 documentation by nostr:npub1yaul8k059377u9lsu67de7y637w4jtgeuwcmh5n7788l6xnlnrgs3tvjmf
- Finishes the migration of People List updates from LiveData to Flow
- Migrates all Refreshable feeds to the Refreshable box component
- Refactors cache methods in GiftWraps
- Migrates Media3 Videos to the DefaultMediaSourceFactory

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.86.0/amethyst-googleplay-universal-v0.86.0.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.86.0/amethyst-fdroid-universal-v0.86.0.apk )

[Changes][v0.86.0]


<a id="v0.85.3"></a>
# [Release v0.85.3](https://github.com/vitorpamplona/amethyst/releases/tag/v0.85.3) - 2024-03-04

- [Displaying issues and Patches in the Notification](https://github.com/vitorpamplona/amethyst/commit/5848255e72fae583057da73a1a2f2d295d4e1d04)
- BugFix: [Cleans up the fork information on new posts has been canceled or posted.](https://github.com/vitorpamplona/amethyst/commit/8299f4cfca41333d3ff212ef723bf86e12e6c080)
- [Displaying correct edits on the new edit proposal](https://github.com/vitorpamplona/amethyst/commit/e9fd62dc26bfa66a59b3da613df522fa21b6a10d)
- [Adds link to the version notes from the drawer](https://github.com/vitorpamplona/amethyst/commit/7bc393143c5e590951f8e499b0caed442d274655)
- [fix crash parsing multiple results from amber](https://github.com/vitorpamplona/amethyst/commit/23718f51dd203020035933af88d54e39c415c215)
- [Adds nostr git for issue management software.](https://github.com/vitorpamplona/amethyst/commit/9081d5a54b9f847e4683de3268681e4a15d2eb4c)
- [Don't show button to edit the post if the author of the original post is not the logged in user](https://github.com/vitorpamplona/amethyst/commit/cd2b5d78a182d386d6197c2040e3b6da53a1aa99)

[Changes][v0.85.3]


<a id="v0.85.1"></a>
# [Release v0.85.1: Medical Data fix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.85.1) - 2024-03-04



[Changes][v0.85.1]


<a id="v0.85.0"></a>
# [Release v0.85.0 Edits, Git, Embed, etc](https://github.com/vitorpamplona/amethyst/releases/tag/v0.85.0) - 2024-03-04

Adds support for post edits, post forks, pull requests, open timestamp, git repositories, issues, patches and replies, wiki pages and some medical data.

New Additions:
- Adds support for editing of notes (NIP-37 / kind: 1010)
- Adds support for sending edit proposals.
- Adds embeded events in NIP-19 uris
- Adds support for NIP-03: OpenTime Stamp attestations (kind: 1040)
- Adds support for Decentralized Wikis (event kind 30818)
- Adds basic support for NIP-34: Git repositories (kind 30617), patches (kind 1617), issues (kind 1621) and replies (kind 1622).
- Adds rendering support for FHIR payloads (kind 82).
- Adds support for the q tag
- Adds early support for Kind1 forks.
- Sets zap splits automatically for quotes and forks
- V4V: Ask for donations in the Notification page
- Adds relay icon rendering from the NIP11 document

Bug Fixes:
- Fixes the text's vertical alignment when emoji's are present
- Fixes DM Chatroom edit button
- Fixes the crash when images are not being present in the image dialog.
- Inserts uploaded URLs where the cursor is and not at the end of the new post.
- Fixes the rendering of Japanese characters, hashtags and custom emojis in the same line.
- Fixes the dissapearance of some Quartz classes when exporting to maven
- Fixing stack overflow with more than 200 zaps in a single note.
- Fixes image preview visualization on a new post
- Adds support for a new report option as Other
- Fixes missing nsec processing when parsing NIP-29 uris
- Fixes caching issue when creating a Bitcoin invoice for the first time
- Fixes UI issues due to the caching of Polls
- Better aligns post header elements
- Fixes bug with NIP-11s with null `kind` arrays
- Fixes quote and repost notes partially disappearing when they contain hidden users or words.
- Fixes content title for the video playback notification

Updated translations:
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)
- Spanish, Spanish, Mexico and Spanish, United States by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Swahili by [@npub1q6ps7m94jfdastx2tx76sj8sq4nxdhlsgmzns2tr4xt6ydx6grzspm0kxr](https://github.com/npub1q6ps7m94jfdastx2tx76sj8sq4nxdhlsgmzns2tr4xt6ydx6grzspm0kxr)
- Czech, German, Swedish and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Arabic by [@npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t](https://github.com/npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)

Performance Improvements:
- Improves the speed of the text parser
- Reduced memory footprint of navigation buttons
- Faster hex validation
- Increases the speed of the Robohash SVG to byte buffer function
- Adds Benchmark tests for the content parser
- Adds Benchmark tests for the url detector
- Speeds up image compositions
- Improves relay list NIP-11 caching
- Faster Longform tag filters
- Speeds up the loop through local cache
- Improves the speed of Location services
- Improves the cache of LnInvoices
- Improves cache of cashu tokens
- Reduces memory footprint for parsed posts.

Code Quality Improvements:
- Moves content parsers and media classes to commons.
- Moves emoji parsers to commons
- Moves Wallet Connect code to Quartz
- Moves Relay information code to Quartz
- Removes dependency on Kotlin serialization
- Adds a release draft generator to CI
- Updates Vico, Compose UI Version, Coil and Google Service dependencies
- Refactors the code to manage extra characters after Bech32 Links
- Moves to Android Studio Iguana | 2023.2.1
- Moves gradle to 8.4
- Moves project to version catalogs

[Changes][v0.85.0]


<a id="v0.84.3"></a>
# [Release v0.84.3: Emoji Passwords in NIP49](https://github.com/vitorpamplona/amethyst/releases/tag/v0.84.3) - 2024-02-16

#Amethyst v0.84.3: NIP-49 Emoji Password fix

If you exported your secret key with any composable Unicode characters as passwords (like an emoji), please export it again.

Bugfixes:
- Counts any quoted post as retweets in the notification stats
- Avoids the need to p-tag the user to count as a mention
- Normalizes passwords to Unicode's NFKC in NIP49
- Adapts Scrypt lib to support empty keys

Code Quality Improvements:
- Refactors clickable text and notification feed filter
- Updates secp256k1

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.84.3/amethyst-googleplay-universal-v0.84.3.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.84.3/amethyst-fdroid-universal-v0.84.3.apk )

[Changes][v0.84.3]


<a id="v0.84.2"></a>
# [Release v0.84.2: Fixes text alignment issues](https://github.com/vitorpamplona/amethyst/releases/tag/v0.84.2) - 2024-02-15

#Amethyst v0.84.2: Text alignment fix

Bugfixes:
- Fixes link misalignment in posts

Updated translations:
- Czech, German, Swedish, and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.84.2/amethyst-googleplay-universal-v0.84.2.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.84.2/amethyst-fdroid-universal-v0.84.2.apk )

[Changes][v0.84.2]


<a id="v0.84.1"></a>
# [Release v0.84.1 Support for NIP-49](https://github.com/vitorpamplona/amethyst/releases/tag/v0.84.1) - 2024-02-14

#Amethyst v0.84.1: ncryptsec support (NIP-49)

Now you can export and log in with a password-protected version of your private key. This new format starts with **ncryptsec** and requires inputting a password to decrypt the key before loading it into a client. Keep in mind that the new format is not designed to replace your **nsec**, but to work side-by-side with it. Keep your nsec in the safest place you can and use the **ncryptsec** to move your key between devices, deleting it as soon as you are done with the transfer.

New Additions:
- Adds support for NIP49 to login and back up key screens
- Adds cryptographic support for NIP-49 to Quartz
- Enables citation on chats via @
- Adds "₿itcoin" to the set of custom hashtags

Updated translations:
- Portuguese by [@npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6](https://github.com/npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- Chinese by [@npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7](https://github.com/npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7)

Performance Improvements:
- Avoids the memory use of the flatten operation on Notification counters
- Adds a check for the main thread when pulling opengraph tags.
- No need to crossfade when clicking on Show More

Code Quality Improvements:
- Updates Compose dependencies

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.84.1/amethyst-googleplay-universal-v0.84.1.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.84.1/amethyst-fdroid-universal-v0.84.1.apk )

[Changes][v0.84.1]


<a id="v0.83.13"></a>
# [Release v0.83.13: Community and Public Chats early support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.83.13) - 2024-02-12

New Additions:
- Adds background support for community and public chat list events from NIP-51
- Removes the confusing notification dot of the discovery tab

Bugfixes:
- Fixes thread rendering when `mention` events are added without mentioning any event.
- Unwrapps the reply message if the GiftWrap was tagged as a reply instead of the correct message id.
- Fixes Send to Top in the marketplace
- Fixes text-to-voice accessibility issues in the main feed UI
- Inverts the order of the hidden users in the security screen: last blocked goes first
- Fixes crash when mute list has `e` tags that are not valid hexes
- Fixes crash when opening an incorrect `nostr:` uri

Updated translations:
- Czech, German, Swedish, and Portuguese by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)

Performance Improvements:
- Moves language translation cleanup to the IO thread since it clears disk files as well
- Small adjustments in the re-use of modifiers

Code Quality Improvements:
- Small refactoring to focus the encrypted storage procedures to the application context.

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.13/amethyst-googleplay-universal-v0.83.13.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.13/amethyst-fdroid-universal-v0.83.13.apk )

[Changes][v0.83.13]


<a id="v0.83.12"></a>
# [Release v0.83.12](https://github.com/vitorpamplona/amethyst/releases/tag/v0.83.12) - 2024-02-06

Performance Improvements:
- Improved scroll performance with faster text parsing tools

[Changes][v0.83.12]


<a id="v0.83.10"></a>
# [Release v0.83.10: Bug Fixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.83.10) - 2024-02-05

#Amethyst v0.83.10: Bug Fixes

New Additions:
- Adds Horizontal Scroll to the action buttons in the New Post screen to partially fix hidden buttons in small/thin screens.

Bugfixes:
- Fixes crash with an invalid custom Zap Amount
- Fixes relay re-connection issues when the relay closes a connection
- Fixes the top padding of the quoted note in a post
- Optimizes memory use of the visual user and url tagger in new posts

Updated translations:
- Persian by [@npub1cpazafytvafazxkjn43zjfwtfzatfz508r54f6z6a3rf2ws8223qc3xxpk](https://github.com/npub1cpazafytvafazxkjn43zjfwtfzatfz508r54f6z6a3rf2ws8223qc3xxpk)
- French and English, United Kingdom by [@npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t](https://github.com/npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t)

Performance Improvements:

Code Quality Improvements:

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.10/amethyst-googleplay-universal-v0.83.10.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.10/amethyst-fdroid-universal-v0.83.10.apk )

[Changes][v0.83.10]


<a id="v0.83.9"></a>
# [Release v0.83.9: NIP-92 support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.83.9) - 2024-02-01

#Amethyst v0.83.9: Support for NIP-92

New Additions:
- [Includes the product in the first message of the marketplace.](https://github.com/vitorpamplona/amethyst/commit/54155a3c30bbf59849b7f42b0b518a92c0552696)
- [Adds support for NIP-92 in public messages and new DMs. NIP-54 stays in NIP-04 DMs](https://github.com/vitorpamplona/amethyst/commit/e56377f8c3f8e8e53844d1cc56786f3072876e42)

Updated translations:
- Ukrainian by lizzz
- Spanish, Spanish, Mexico and Spanish, United States by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)
- Arabic by [@npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t](https://github.com/npub13qtw3yu0uc9r4yj5x0rhgy8nj5q0uyeq0pavkgt9ly69uuzxgkfqwvx23t)

Code Quality Improvements:
- [Updates to Android Studio Hedgehog | 2023.1.1 Patch 2](https://github.com/vitorpamplona/amethyst/commit/34d373c293db368173b66b71bba0c90fe584b389)

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.9/amethyst-googleplay-universal-v0.83.9.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.9/amethyst-fdroid-universal-v0.83.9.apk )

[Changes][v0.83.9]


<a id="v0.83.8"></a>
# [Release v0.83.8 Bug Fixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.83.8) - 2024-01-31

#Amethyst v0.83.8: Bug Fixes

New Additions:
- Removes the need for Amber's package name in the androidManifest for the external signer by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Adds a longpress to copy url to the url preview card by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Adds support for always rejected permissions from external signer by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Adds the exception descripton to the Zap error message.

Bugfixes:
- Fixes secondary buttons theme in the login and signup screens.
- Fixes vertical misalignment of some npubs in the middle of the note.
- Fixes NPE when accounts are not present when resuming the app in a group
- Fixes missing language options for Greek by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Fixes content sensitivity for NIP-54 images.
- Fixes proxy setup when de/activating Tor / changing ports by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Fixes remember of the wrong hashtag because it's a different post.

Updated translations:
- Czech, German, Swedish and Portuguese, Brazilian by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Spanish, Mexico and Spanish, United States by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)
- French by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- Serbian by [@npub187h9tymz5j6vhyl26kl74yh6yzqzpjec9806w7taey2zefytlmdsttx7v2](https://github.com/npub187h9tymz5j6vhyl26kl74yh6yzqzpjec9806w7taey2zefytlmdsttx7v2)

Performance Improvements:
- Makes sure cancellation of coroutines stops long processes.

Code Quality Improvements:
- Makes the benchmark module profileable
- Updates dependencies

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.8/amethyst-googleplay-universal-v0.83.8.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.8/amethyst-fdroid-universal-v0.83.8.apk )

[Changes][v0.83.8]


<a id="v0.83.7"></a>
# [Release v0.83.7: New Sign up screen](https://github.com/vitorpamplona/amethyst/releases/tag/v0.83.7) - 2024-01-11

New Additions:
- [New Signup screen](https://github.com/vitorpamplona/amethyst/commit/7b7e3624acef4f502eb82116098e3fd05f972d6d)
- [Reduces the size of the following icon](https://github.com/vitorpamplona/amethyst/commit/d27b9ae5a84e753b04592daa5b17cacacbd39dca)

Bugfixes:
- [Fixes the BookmarkScreen update after adding and removing a bookmark to the list](https://github.com/vitorpamplona/amethyst/commit/383d859544d55eae71b41c2be4a4d78b7ebfce11)
- [Solving viewModel creation bug for Public Bookmarks](https://github.com/vitorpamplona/amethyst/commit/81400eb863cdca70ad65f78eeb295e2c3ffd340a)
- [Fixes bug when creating bookmarks for the first time.](https://github.com/vitorpamplona/amethyst/commit/422446f53e15c2ae7e22449d3e4eb4480d8f0769)
- [Fixes bug in checking bookmarks without running the onReady.](https://github.com/vitorpamplona/amethyst/commit/f1e721f32378bd5a9cc512e6801f9dea71235946)
- [Fixes some of the margins in the login screen.](https://github.com/vitorpamplona/amethyst/commit/8f31c60cd1d2c70973ef1e90d89ae7592f652703)

Updated translations:
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- Serbian by [@npub187h9tymz5j6vhyl26kl74yh6yzqzpjec9806w7taey2zefytlmdsttx7v2](https://github.com/npub187h9tymz5j6vhyl26kl74yh6yzqzpjec9806w7taey2zefytlmdsttx7v2)

Code Quality Improvements:
- [Improves dev previews](https://github.com/vitorpamplona/amethyst/commit/9234f4b7a8f6f069bfa957842da5f5e9b636dbac)
- Updates libraries and build tools on GitHub Actions
- Updates dependencies

[Changes][v0.83.7]


<a id="v0.83.5"></a>
# [Release v0.83.5: flare.pub support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.83.5) - 2024-01-06

BugFix: [Makes sure the relay list contains unique urls.](https://github.com/vitorpamplona/amethyst/commit/6e99e6f4a241be85cb46aab607b47d0f8b513e9e)
[Fixes the lack of relay icons in the NIP44 messages](https://github.com/vitorpamplona/amethyst/commit/0be1f8936829c0009f33a584a816d84b7e49a520)
[Fixing some of the minification issues.](https://github.com/vitorpamplona/amethyst/commit/5c7e9258c1cbf869bfde4c5f054e4a73bbac3cc1)
[Creating the benchmark build type on the modules as well.](https://github.com/vitorpamplona/amethyst/commit/f877b6ff6805b07ce05c447f992a18c8ae9a8e3c)
[Adds spotless](https://github.com/vitorpamplona/amethyst/commit/ec867ae8a2df0f6afdf05a0aa5512280e2011aa7)
[Improves the speed of contains](https://github.com/vitorpamplona/amethyst/commit/620b2bfa9fee9e25649d1ab3e79db93090885a1e)
[Updates dependencies](https://github.com/vitorpamplona/amethyst/commit/4291a58f85789bea412c27f94b21b9c09a223153)
[Fixes the benchmark for Robohash](https://github.com/vitorpamplona/amethyst/commit/00cffc8b6b35f1c8a76168b1cd5a4958a8d997d9)
[Fixes the giftWrap test cases since the migration to v2](https://github.com/vitorpamplona/amethyst/commit/7143d786635f66d83975305449ee700fcc3774ba)
[Fixes the position of the participant list in live streams.](https://github.com/vitorpamplona/amethyst/commit/eaaa9a6446799237878e767386925f29f2b13077)
[Initializing the isHidden state for the note correctly](https://github.com/vitorpamplona/amethyst/commit/89fb83cd9aa3420bd8328350036ae00f24ee3dcc)
[Avoiding the creation of modifiers.](https://github.com/vitorpamplona/amethyst/commit/2355a95202cc43aa7c3a48505a97543bc4e9e30b)
[no need to remember the showProfilePicture state](https://github.com/vitorpamplona/amethyst/commit/53a320b4dcfaf7caecafd7e20ee801ea5b6c0cb3)
[Adds support for displaying video events.](https://github.com/vitorpamplona/amethyst/commit/2de3d19a34b97c012e39b203070d9c1c0b1f0520)
[Refactoring some of the old nomenclature of Kind 1 tags.](https://github.com/vitorpamplona/amethyst/commit/57430c43662a18d9b5764d920cba6c1e8c6eeca6)
[Add write support for NIP-10 deprecated positional tags in text notes to maximize backwards compatibility](https://github.com/vitorpamplona/amethyst/commit/9101fb1e9ebd69f25b2237fe99361fb0f3b42060)

[Changes][v0.83.5]


<a id="v0.83.4"></a>
# [Release v0.83.4: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.83.4) - 2023-12-27

- [Fixes reconnecting issue when DataSources were not active and become active later.](https://github.com/vitorpamplona/amethyst/commit/1cedc8a6c83f4e663e3d5b0a898f461ea687f859)
- [Initial support for separate vertical and horizontal videos.](https://github.com/vitorpamplona/amethyst/commit/4afb0027fb7b8f2ab6e6919b1d836899a00203fe)
- [Adds support for NIP-75 Zap Goal events](https://github.com/vitorpamplona/amethyst/commit/48aa9f950dfab7c2b9556b670efb040204cd982a)
- [Fixing the zap of live streams to go to the Host and not the pubkey in the event.](https://github.com/vitorpamplona/amethyst/commit/ec4981852ba651c643308c3a22f761052bf6e53a)
- [Fixes the loading of reactions and zaps to replaceable events.](https://github.com/vitorpamplona/amethyst/commit/77eb0663627a8afc0e87376a2a2fae4e85a5d14c)
- [Improves the design of the discovery cards for Live Activity and Chats.](https://github.com/vitorpamplona/amethyst/commit/abef3d13f27d283305ea428e5a176eb983c456d7)

[Changes][v0.83.4]


<a id="v0.83.3"></a>
# [Release v0.83.3: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.83.3) - 2023-12-23



[Changes][v0.83.3]


<a id="v0.83.1"></a>
# [Release v0.83.1 NIP-44v2](https://github.com/vitorpamplona/amethyst/releases/tag/v0.83.1) - 2023-12-22

#Amethyst v0.83.1: NIP-44 DMs are up!

New Features
- Moves DMs to the audited NIP-44v2
- Adds support for NIP-31 alt tags
- Adds a k-tag to reactions
- Adds i18n for error messages when uploading images

Performance Improvements:
- Improves the performance of Robohash
- Add less memory-intensive timeAgo calculations and translations
- Uses primitives instead of the wrapped object in several places
- Moves to a less memory-intensive way to write and send filters to the server.
- Refines recompositions of routes and bottom icons
- Avoids the creation of new sets when looping through cached maps of User and Notes
- Avoids recreating the EOSE array when changing filters
- Reuses SessionToken for all Playback connections
- Improving the memory use of concurrent hashmaps and immutable collections
- Reduces the use of remember for fixed UI modifiers

Bugfixes:
- Detects URL mime-types by pinging the server instead of relying on the url's extension
- Fixes bug with cropped joinToString assemblies of relay filters
- Avoids Concurrent Modification Exception on the EOSE markups
- Forces nip95 to be under 80Kb to make sure relays can receive it
- Fixes bug that error messages wouldn't show an error when uploading images to the reels page
- Fixes post video dimensions when the user has selected not to load videos automatically
- Updates dependencies

Updated translations:
- Chinese by [@ra5pvt1n](https://github.com/ra5pvt1n)
- Finnish by [@npub1ust7u0v3qffejwhqee45r49zgcyewrcn99vdwkednd356c9resyqtnn3mj](https://github.com/npub1ust7u0v3qffejwhqee45r49zgcyewrcn99vdwkednd356c9resyqtnn3mj)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)
- Dutch by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- Tamil by [@npub1q6ps7m94jfdastx2tx76sj8sq4nxdhlsgmzns2tr4xt6ydx6grzspm0kxr](https://github.com/npub1q6ps7m94jfdastx2tx76sj8sq4nxdhlsgmzns2tr4xt6ydx6grzspm0kxr)

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.1/amethyst-googleplay-universal-v0.83.1.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.83.1/amethyst-fdroid-universal-v0.83.1.apk )

[Changes][v0.83.1]


<a id="v0.82.3"></a>
# [Release v0.82.3: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.82.3) - 2023-12-14



[Changes][v0.82.3]


<a id="v0.82.2"></a>
# [Release v0.82.2: Markdown improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.82.2) - 2023-12-13

- Fixes the transition between short preview and full text on markdown
- Adds NIP-44 metatags to markdown rendering.
- Adds background video rendering on markdown
- Performance: Calculates the text width of a space outside a composable.
- Fixes the image preview with uppercase extensions.

[Changes][v0.82.2]


<a id="v0.82.1"></a>
# [Release v0.82.1: NIP-96, NIP-44, Selling](https://github.com/vitorpamplona/amethyst/releases/tag/v0.82.1) - 2023-12-12

Adds support for selling and listing in Amethyst as well as NIP-96 Image Servers and NIP-54 inline metadata. Provides large improvemetns to Cashu's token redemption and fixes several bugs.

https://image.nostr.build/40ae418ccc5336e17b5949bacc11c31835603437816f8bf867c171f07d34dd54.jpg#m=image%2Fjpeg&dim=720x1612&blurhash=%5BLFFgJMyj%5Bt74TMyoft70LxufiV%5B_Nt7f6WB4TogoMj%5Bxut7ofWAS%7EofbFjtD%25xtWBWBs%2BM%7BjbbH&x=c3a3f49c017f58749226f8ae6021c11a745d2354f52a229cb99eef4a9d20ec39

- Adds selling: ShopStr's classified creation
- Migrates old image server uploads to NIP-96
- Adds support for NIP-54 inline metadata
- Adds a Marketplace tab to Discovery
- New Cashu Redeeming card UI.
- Shows the blurhash with a Download icon instead of the URL when the user chooses to not automatically load images/videos
- Improves the video switching flicker from blurhash to video
- Optimizes the rendering of the drawer
- Updates EOSE status in the same thread of the new event to reduce the amount of coroutine launches.
- Uses just one HTTPClient for the entire app
- Adds a User Agent to all HTTP requests.
- Improves Cashu Redeeming UI feedback
- Adds support for the FileServers kind
- Adds relay information for Replaceable events
- Unifies upload options into NIP-94 images
- Improves the rendering of inline metadata
- Uses nostr.wine instead of filter.nostr.wine as a search relay
- Fixes bottom bar appearing in chats when the keyboard is open
- Fixes uploading crash due to malformed video formats
- Fixes crash when image is an SVG and tries to compress
- Fixes deletion of replaceable events
- Fixes hash calculation from the entire payload to only the bytes in the file
- Fixes bug when updating relay list that used keep the previous list
- Presents better error messages when the image upload fails
- Adds a button to Cashu preview to redeem on external wallet by [@npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8](https://github.com/npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8)
- Fixes zap splits when using amber with intents by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Updates translations for cs/de/sv/pt by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Updates Hungarian translations by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)
- Updates Finnish translations by [@petrikaj](https://github.com/petrikaj)
- Updates Dutch translations by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)
- Updates French translations by [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Updates dependencies

[Changes][v0.82.1]


<a id="v0.81.5"></a>
# [Release v0.81.5](https://github.com/vitorpamplona/amethyst/releases/tag/v0.81.5) - 2023-11-30

- [Fixes blocked lists on Videos.](https://github.com/vitorpamplona/amethyst/commit/efb9814f1b28b70a2dfdeb879b4bd89b327fa1c8)
- [Pre-loads profile/mute list information for all the logged in accounts in the app.](https://github.com/vitorpamplona/amethyst/commit/b2b7cba352177e1c409883d437e8b815b08d0519)
- [Fixes bug when creating accounts in the background and trying to create the live set](https://github.com/vitorpamplona/amethyst/commit/47ae42e3fed1a45b940df5938cb052665a33f31f)
- [Makes relay pool coordinator thread-safe. Forcing the disconnect of an old relay list before connecting to a new one.](https://github.com/vitorpamplona/amethyst/commit/5db3ede09e8a17899ed61c36e106e7911fc944bd)
- [Avoids sending filters with empty follow lists on Videos](https://github.com/vitorpamplona/amethyst/commit/5347373cdeb5b8756aa8be0035f23634902fafe8)
- [Forces websocket closure onFailure](https://github.com/vitorpamplona/amethyst/commit/05596cb9bf6a4a06490e6e232dad19df65eb176e)
- [Fixes: emitting an empty follow list if it cannot decrypt it](https://github.com/vitorpamplona/amethyst/commit/b021920eaa1f95fb7f35b3d8f61577e9f4236223)
- [Caches zap calculations in notification cards.](https://github.com/vitorpamplona/amethyst/commit/2b27ac3aec64f5c91bef404921abc8d46e1f3d57)
- [Fixes null list names showing before the list is loaded](https://github.com/vitorpamplona/amethyst/commit/c692c9125b846f5f7caeb073ba10233f3e0ad485)
- [Fixes hidden buttons in the Chat floating button](https://github.com/vitorpamplona/amethyst/commit/7322fd7aae3d51817cd288b9220da4c92f17c37b)
- [Fixes all-or-nothing decryption procedure for Zap lists which were failing in a few cases.](https://github.com/vitorpamplona/amethyst/commit/07a92cd70d0e357da050c5e95e4a9ad1acc6c280)

[Changes][v0.81.5]


<a id="v0.81.3"></a>
# [Release v0.81.3: NIP-88](https://github.com/vitorpamplona/amethyst/releases/tag/v0.81.3) - 2023-11-29

#Amethyst v0.81.3:

- Massive refactoring to unify our internal signer with Amber's signer in all supported events
- Adds [NIP-88](https://github.com/nostr-protocol/nips/pull/901) NOTIFY request support
- Migrates our Block list to kind:10000
- Fixes the breaking of [@npubs](https://github.com/npubs) when other words are combined with the nostr address
- Adds default encryption and decryption permissions to the Amber login call to avoid multiple Amber screens open at once by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Adds support for sending/receiving/approving multiple events at once by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Adds a chat with seller flow to ShopStr's event rendering.
- Reduces the amount of downloads to build the Notification chart of the week
- Immediatly force-closes the WebSocket when leaving the app
- Narrows the re-downloads of event reactions down
- Fixes the blue notification dot appearing when the user receives a notification from a blocked account before downloading the blocklist
- Fixes URL Preview card when websites use a blended version of multiple open graph specs
- Adds a geohash mipmap to event tags
- Reduces multiple reconnections to relays when the app cold starts.
- Adds back arrow button to the top of the Nav bar of the Thread view.
- Fixes race conditions when opening videos at the same time
- Fixes spacing when drawing POW and Geolocation at the same time
- Runs the translation as the UI Scope instead of ViewModel's
- Migrates the event's tag list from List to Array to save some bytes.
- Increases connection timeouts when on mobile data.
- Improves the EOSE logic when creating filters by grouping filter requests with similar `since`clauses
- Fixes video release coroutine being killed by Android, leaving the Video playing in the background
- Adds a cache of the total amount of Zaps per note
- Allows sat amounts up to 4 digits without abbreviation [@vicariousdrama](https://github.com/vicariousdrama)
- Improves the rendering of LN Invoice Previews.
- Fixes error message when parsing LnInvoice
- Updates several dependencies

Updated translations for:
- Czech, German, Swedish and Portuguese, Brazilian by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Spanish, Mexico and Spanish, United States by [@npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903](https://github.com/npub1luhyzgce7qtcs6r6v00ryjxza8av8u4dzh3avg0zks38tjktnmxspxq903)
- Hungarian by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)
- Chinese Simplified by https://crowdin.com/profile/stella2023
- Persian by [@npub1cpazafytvafazxkjn43zjfwtfzatfz508r54f6z6a3rf2ws8223qc3xxpk](https://github.com/npub1cpazafytvafazxkjn43zjfwtfzatfz508r54f6z6a3rf2ws8223qc3xxpk)

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.81.3/amethyst-googleplay-universal-v0.81.3.apk )
- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.81.3/amethyst-fdroid-universal-v0.81.3.apk )

[Changes][v0.81.3]


<a id="v0.80.7"></a>
# [Release v0.80.7](https://github.com/vitorpamplona/amethyst/releases/tag/v0.80.7) - 2023-11-08

- Migrates external sharing service to njump.me by @ @[es09l4ps](https://coracle.social/people/nprofile1qqs05qt95rce97cwj8rasugw2ats45nmxu2u55scrak98jdjqvuhqucprpmhxue69uhkv6tvw3jhytnwdaehgu3wwa5kuef0qy88wumn8ghj7mn0wvhxcmmv9uq3qamnwvaz7tmp9ehx7uewd3hkctc9zwssw)
- Adds support for Greek by @ @[csavastel](https://coracle.social/people/nprofile1qqs8wfdhfs8hvnsg265mngx7vwkvmtg34zglecmqlmc9mzje6cq7sqspzamhxue69uhhyetvv9ujumn0wd68ytnzv9hxgtcpz4mhxue69uhhyetvv9ujuerpd46hxtnfduhszynhwden5te0dehhxarjxgcjucm0d5hsky9vyw)
- Adds support for Indonesian by @ @[Yonle](https://coracle.social/people/nprofile1qqsrg73rwzgq6xd5u36kyg2ef69a5ur2uhrcthsfuk0yvp0ergplf8qpzemhxue69uhkymmnw3ezuemvd96xx6pwd4jj7qfqwaehxw309a3x7um5wgh8jmmwd3jjumr9vd682unfveujumn9wshszxrhwden5te0ve5kcar9wghxummnw3ezuamfdejj7wgxqka) and @ @[3ssspdkl](https://coracle.social/people/nprofile1qqsvxj2am3fs0xunr7rj0elhg2xf8utpyfnzzrq2mnlqjtraag8s5ccprpmhxue69uhkv6tvw3jhytnwdaehgu3wwa5kuef0qy88wumn8ghj7mn0wvhxcmmv9uq3qamnwvaz7tmp9ehx7uewd3hkctcjw5pr7)
- Updates Spanish translations by @ @[⚡₿it₿y₿it⚡](https://coracle.social/people/nprofile1qqs07tjpyvvlq9ugdpax8h3jfrpwn7kr72k3tc7ky83tggn4et9eangprpmhxue69uhkv6tvw3jhytnwdaehgu3wwa5kuef0qy88wumn8ghj7mn0wvhxcmmv9uq3qamnwvaz7tmp9ehx7uewd3hkctcw534ek)
- Updates Arabic translations by @ @[fqwvx23t](https://coracle.social/people/nprofile1qqsgs9hgjw87vz36jf2r83m5zree2q87zvs8s7kty9jljdz7wprytysprpmhxue69uhkv6tvw3jhytnwdaehgu3wwa5kuef0qy88wumn8ghj7mn0wvhxcmmv9uq3qamnwvaz7tmp9ehx7uewd3hkctckmawlz)
- Fixes position of video controlling buttons when top bars are present in full screen.
- Fixes bug when the app calls isAcceptable directly, bypassing the other checks in AccountViewModel
- Fixes race condition when pausing and restarting relay connections
- Updates Kotlin compiler version
- Removes a recomposition between the started state and the isOnline state that is already cached.
- Migrates the check if stream is online to a single compose object.
- Forces relay reconnection when a new WIFI service is available
- Fixing translations of the that create the same message but with different character cases
- Refines the layout of Author Pictures for performance
- Refines layout of URL Previews for performance
- Refines the padding of chat messages and reaction row
- Correctly highliting a notification card on touch

[Changes][v0.80.7]


<a id="v0.80.4"></a>
# [Release v0.80.4: Performance Optimizations](https://github.com/vitorpamplona/amethyst/releases/tag/v0.80.4) - 2023-10-30

- Starts videos from Main, but in a thread.
- Fixes new line of Loading Animation and Download buttons for images.
- Minimizes Jittering when loading videos.
- Improving rendering of reaction types
- Only updates notification dots once at every 3 seconds.
- New users now follow themselves by default
- Fixes Floating Action Button not showing again after changing screens by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Updates French, Dutch, Hungarian, Czech, German, Portuguese and Swedish translations by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp) [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef) [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd) [@npub1rq9x6sk86e8ccw2cm8gsm4dyz9l24t823elespupaxjnzdk026fsca2r93](https://github.com/npub1rq9x6sk86e8ccw2cm8gsm4dyz9l24t823elespupaxjnzdk026fsca2r93) and [@npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz](https://github.com/npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz)
- Fixing colors of edit buttons
- Login and Logout already in IO threads
- Performance: Stable class review.
- Adds images and posts to notes without an extra line.
- Fixes: Direct replies have "reply" marker instead of "root" marker
- Speeds up the boost count method
- Fixes weird alignment of multi-row post titles from highlighter.
- Fixes the update on Profile Feed when Blocking/Unblocking the user.
- Refines Markdown to match Material3 Style
- Reduces the font size of Subject labels
- Fixes the use of decimals on Notification's chart

[Changes][v0.80.4]


<a id="v0.80.2"></a>
# [Release v0.80.2: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.80.2) - 2023-10-28

- [Slight improvement in the performance of Reaction Row's rendering](https://github.com/vitorpamplona/amethyst/commit/2cc9bb8b720ae63716d27d2aa89ba1a3b1a225be)
- [Reducing the size of Subject labels](https://github.com/vitorpamplona/amethyst/commit/553bebb6cf5d362819b3e5c4b17c5b8f9c57d2a5)
- [Refining some of the Markdown to match Material3 Style](https://github.com/vitorpamplona/amethyst/commit/e91e5c366d6d2baaf61eba3df040101bb0a93528)
- [Update Profile Feed when Blocking/Unblocking the user.](https://github.com/vitorpamplona/amethyst/commit/9d32718c1304dd8174bdf22dbb6fc710ae002660)
- [Reverts the New Post button animation due to bugs of not coming back](https://github.com/vitorpamplona/amethyst/commit/64c9505620c55b5085aa3710f88df08b5c072d94)
- [Fixes the use of decimals on Notification's chart](https://github.com/vitorpamplona/amethyst/commit/75dc55858cab5667cd27827b9fd52df1014e38f7)
- [Reverts the hidden note LiveData to make sure the interface doesn't blink with the hidden note that just disappears ms later.](https://github.com/vitorpamplona/amethyst/commit/c6062120434b3c442ff26d0a20ede140837f7179)
- [fix typo in resource string name](https://github.com/vitorpamplona/amethyst/commit/e89384ee6181bc197daa18f0bf2604fc60defdad) [@davotoula](https://github.com/davotoula)
- [Updated translations for cs/de/sv/pt](https://github.com/vitorpamplona/amethyst/commit/bb324776716be5dad4e26f3315b53ee01cd4d076) by [@davotoula](https://github.com/davotoula)

[Changes][v0.80.2]


<a id="v0.80.1"></a>
# [Release v0.80.1: Foss Push Notifications](https://github.com/vitorpamplona/amethyst/releases/tag/v0.80.1) - 2023-10-27

- Adds support for Unified Push in the FOSS edition by [@KotlinGeekDev](https://github.com/KotlinGeekDev)
- BugFix for missing push notifications when using Google's edition
- Creates a ViewModel store for each user, which allows faster memory cleanup when switching accounts.
- Fix crash when uploading images/videos using external signer by [@greenart7c3](https://github.com/greenart7c3)
- Adds Lifecycle to all Flow collects in compose to stop processing new events when the app is paused.
- Avoids creation of the LiveData in every recomposition
- Removing Stop with Task from PushNotifications to make sure the PushService remains active.
- Updated translations to Sweden, Portuguese, Czech and German by [@davotoula](https://github.com/davotoula)

[Changes][v0.80.1]


<a id="v0.79.13"></a>
# [Release v0.79.13: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.79.13) - 2023-10-23

[Makes sure only one npub is logged in at the same time](https://github.com/vitorpamplona/amethyst/commit/191254d920ce5a78ed141d1387a2785f551996a8)
[Update UI dependencies](https://github.com/vitorpamplona/amethyst/commit/91bfb60613436abb494d02b10f61da9ee94a2027)
[Making sure the UI update from language is in the Main thread.](https://github.com/vitorpamplona/amethyst/commit/9dfb4d1a1b7aa06b903d477f83b8c9416707443b)
[Solves NPE when the TextToSpeech engine isn't ready.](https://github.com/vitorpamplona/amethyst/commit/f92e13270de30f9a063da3b2a9c9740387e4b5c5)
[Fixes status update field when more than one status is available.](https://github.com/vitorpamplona/amethyst/commit/10a0dc7f8aa103013ddd4363e78e2e2ecefc254f)
[Moves Relay and User Metadata update buttons from Post to Save.](https://github.com/vitorpamplona/amethyst/commit/5b0fc7982b86575ee5cad012fb346de63aaa45b4)
[Slight adjustment on the rendering of hashtags.](https://github.com/vitorpamplona/amethyst/commit/6831349f2fe1b9082309865071014cb17d0e2aea)

[Changes][v0.79.13]


<a id="v0.79.12"></a>
# [Release v0.79.12: Tablet Layouts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.79.12) - 2023-10-22

- Adds a [Tablet view on chats](https://github.com/vitorpamplona/amethyst/commit/80df2eefed1c7cb1174eecacac2c2b632437ba98)
- [TwoPane display for chats in tablets and folds.](https://github.com/vitorpamplona/amethyst/commit/884a124c7e534f18e15ad20c549d5d0b5204f98c)
- [Fixes orientation changes when in full screen](https://github.com/vitorpamplona/amethyst/commit/a284c1b9c665327fa9a80ff521ef11c3e59fbd22)
- [Updates Jsoup](https://github.com/vitorpamplona/amethyst/commit/ccf428eff6bb58145f78ae2a255b63989a512df9)

[Changes][v0.79.12]


<a id="v0.79.10"></a>
# [Release v0.79.10: Full Blacks](https://github.com/vitorpamplona/amethyst/releases/tag/v0.79.10) - 2023-10-21



[Changes][v0.79.10]


<a id="v0.79.9"></a>
# [Release v0.79.9: Full screen videos/images](https://github.com/vitorpamplona/amethyst/releases/tag/v0.79.9) - 2023-10-20

- [Solving one of the OutOfMemory errors (too many translators instantiated at the same time)](https://github.com/vitorpamplona/amethyst/commit/3321448dd29a74ca4d91fc2dd08590dcb12d265e)
- [Fixes the spacing of the account switcher to avoid getting too close to navigation buttons.](https://github.com/vitorpamplona/amethyst/commit/8065b942bd6b1c5f0ff714298ded19de4388a775)
- [Fixes full-screen dialog for videos and images](https://github.com/vitorpamplona/amethyst/commit/17e59c5ae882438c463433154e3222a0bd521bf3)
- [Activates single-tap to double zoom when on full-screen dialog](https://github.com/vitorpamplona/amethyst/commit/39d84e33bb2d829db45768db399071b24afef983)
- [Fixes caching issue of the saved list selection in the top bar on cold starts](https://github.com/vitorpamplona/amethyst/commit/87e8948d9ae4ed1419180697ca01e9ff41cc87d7)
- [Fixes one of connectivity issues that kept the app off-line even though the phone is connected to the internet](https://github.com/vitorpamplona/amethyst/commit/1ab93adc22b69db3c181ebe886fe7c0cf15fd205)
- [fix double encryption when sending dms, when login with external signer set privKey to null to be sure we have no private keys](https://github.com/vitorpamplona/amethyst/commit/800c0b0131cdc4124cdfece23159d852db53b60d) by [@greenart7c3](https://github.com/greenart7c3)

[Changes][v0.79.9]


<a id="v0.79.7"></a>
# [Release v0.79.7](https://github.com/vitorpamplona/amethyst/releases/tag/v0.79.7) - 2023-10-18

- [Removing bold titles](https://github.com/vitorpamplona/amethyst/commit/27286c7ffeab8239c4925e4a3297c3e9efc47d79)
- fix crash when sending giftwraps with amber
- Moves app startup to an IO thread.
- Upgrades Shared Preferences serialization to a Single JSON object
- Simplifies Shared Preferences state changes
- LazyInitializes Video cache

[Changes][v0.79.7]


<a id="v0.79.6"></a>
# [Release v0.79.6](https://github.com/vitorpamplona/amethyst/releases/tag/v0.79.6) - 2023-10-17

[Stops PushNotifications when the app is killed.](https://github.com/vitorpamplona/amethyst/commit/35f6ecad59c8ac90478d535003c196dcb732f4f4)
[Dump memory states to debug OOO](https://github.com/vitorpamplona/amethyst/commit/2a328ca12057769a1c8e8913649802aecea627bb)
[Only changes Username if value changes.](https://github.com/vitorpamplona/amethyst/commit/1e83dbfbe356ef050ac34ffdb3ce094b0d6e8181)
[Added #thenostr hashtag icon by @believethehype ](https://github.com/vitorpamplona/amethyst/commit/3ea7b8194d6f86056962e9d4445b96599cb74b2f)
[Activates color for the zaps in the Notification chart](https://github.com/vitorpamplona/amethyst/commit/1a7ae33fa867ef38b511a8193b5a290d7cc82598)
[Removes configuration cache: was creating a lot of build issues](https://github.com/vitorpamplona/amethyst/commit/2bbe126c8d2d5aab8698e093145c2d02d909be77)
[Improving Git hooks](https://github.com/vitorpamplona/amethyst/commit/f2e6efe2b7761429e56797a2d91e758aabfb8074)
[Moves playback services to a package](https://github.com/vitorpamplona/amethyst/commit/404e6cd8627f8a583e085cb051d28db7aa15b16c)

[Changes][v0.79.6]


<a id="v0.79.5"></a>
# [Release v0.79.5](https://github.com/vitorpamplona/amethyst/releases/tag/v0.79.5) - 2023-10-17

- [Forces a filter reset after authentication for inbox.nostr.wine](https://github.com/vitorpamplona/amethyst/commit/611e8f1a6d75068dcc63809414029ce9fc5627b7)
- [BugFix: Resolves a duplicated entry on relay list of Notes](https://github.com/vitorpamplona/amethyst/commit/c619a9000c58d8ddd3a6f582c5c4a90ea822b1e9)
- [Avoids testing the signature when the id or sig fields are blank](https://github.com/vitorpamplona/amethyst/commit/c29b4b8e5f53fccdc2df245aa599f8c68ab5d09c)
- [Improves the feedback to messages from relays.](https://github.com/vitorpamplona/amethyst/commit/4286b64b415813f2f8e61ed1655668d9d4b2c7d8)
- [add exception handling for parsing geohash by @jiftechnify ](https://github.com/vitorpamplona/amethyst/commit/407807d8711fd2d20f1ffd8b66e413f80bc754ba)
- [Only adds a border to video controls when in full screen](https://github.com/vitorpamplona/amethyst/commit/e1e42ed500f5ea4b38a1efcac1af265dd70245fb)
- [Activates images controllers on click](https://github.com/vitorpamplona/amethyst/commit/1767cc74a978bbfd8329393993a0488990dd99db)
- [Adds a border in the image dialog to avoid overriding controllers.](https://github.com/vitorpamplona/amethyst/commit/4028018605461f9e8f209b993b5557453e3c553d)
- [Image bleeding into separate page bugfix](https://github.com/vitorpamplona/amethyst/commit/33f8b6d6d86912fdc04f28a6cda3aff7367cd167)
- [Update dependencies](https://github.com/vitorpamplona/amethyst/commit/f9fed8a04f6a801e5c4597bb1e024eaedda11bbb)
- [Fixes all the other mute list feeds](https://github.com/vitorpamplona/amethyst/commit/ba4a594a41743ef081f66b53ec7260eec212282d)
- [fix double encryption on nip04 by @greenart7c3 ](https://github.com/vitorpamplona/amethyst/commit/173245d9003fd95c619cca4407b6604d0f2402b1)
- [try to fix messages being encrypted twice by @greenart7c3](https://github.com/vitorpamplona/amethyst/commit/5bc4aab8d809303f64485ded74465d65554901bb)
- [change mute test to equality with string including event 30000 and user hex by @davotoula ](https://github.com/vitorpamplona/amethyst/commit/c7861dfac44a58a8b5d574f1c4eb42d10649df4c)
- [Fixes visibility of the bottom and top bar when the user comes back](https://github.com/vitorpamplona/amethyst/commit/275051ed308b4bfeb960bcf859248424579a1573)
- [Fixes failure to load long form content with d tags reserved url chars](https://github.com/vitorpamplona/amethyst/commit/6748abbf70416e36a99a16ba02f819fe2296c6e1)
- [Fixes bug of not immediately loading chat rooms](https://github.com/vitorpamplona/amethyst/commit/e04a35afed3df7d3572e22d23029c2773bbe591f)
- [Moves the Post button on chat screens to a > Button](https://github.com/vitorpamplona/amethyst/commit/f67ff43eb36fe1fdda2afee70c581fb65d04bb42)
- [Hide's the Video Full Screen dialog buttons together with video](https://github.com/vitorpamplona/amethyst/commit/b8b41f840a326c248dc41b838c0d96cffa5988f1)
- [Fixes the saving of the video position between screens](https://github.com/vitorpamplona/amethyst/commit/e8c9e73985d9ef0c64bd9d19e85846f8cfaa26e0)
- [Updated translations for cs/de/sv](https://github.com/vitorpamplona/amethyst/commit/56c67b9ec6c256c35c2296eb4fee1b457befe462)
- [Moves Following icon from crossfade to animatedVisibility.](https://github.com/vitorpamplona/amethyst/commit/7eddf4a12a525f4522c03ce57504506c77119607)
- [Breaks down Compose components in the Discovery tab.](https://github.com/vitorpamplona/amethyst/commit/cec204b7ae1bf625cd923298cdc8fe9320d4ddb8)
- [Allows Base64 images on profiles.](https://github.com/vitorpamplona/amethyst/commit/188ef3762d7c249a52abf1a1d9baa7c9fff797a0)
- [Fixes bug of messages not reappearing after memory trimming.](https://github.com/vitorpamplona/amethyst/commit/0da031fae4952ca8259b52833fe3cce6e4738e9d)
- [Moves activeOnScreen calculations to a LaunchedEffect](https://github.com/vitorpamplona/amethyst/commit/72dff060d2e295324d0c6161f253e261ab556577)
- [Only changes shouldShow if the value is different.](https://github.com/vitorpamplona/amethyst/commit/d6f4ffafa16517f678eb3623b28e4a935c2222f0)
- [Moves audiothumbnail loader to the viewModel scope](https://github.com/vitorpamplona/amethyst/commit/4b362176990deaa39ff0278da070994d598b1277)


[Changes][v0.79.5]


<a id="v0.79.2"></a>
# [Release v0.79.2: Performance updates](https://github.com/vitorpamplona/amethyst/releases/tag/v0.79.2) - 2023-10-01

- Fixes the repeat payment requests on zaps when the NWC is not setup.
- Improves error zap message screens for Material3
- Uses Lists name/title instead of d-tag on the Top Bar
- Increases the performance of the Bottom bar's Notification dot calculations
- Speeds the composition of the Topbar
- Uses the "host" tag as the creator of the stream.
- Moves check if the stream is online process to the viewModel scope.
- Moves many Toasts to the better designed Information Dialogs.
- Speeds up cold startup.
- Makes sure shared flows don't suspend.
- Updates dependencies

[Changes][v0.79.2]


<a id="v0.79.0"></a>
# [Release v0.79.0 material 3](https://github.com/vitorpamplona/amethyst/releases/tag/v0.79.0) - 2023-09-29



[Changes][v0.79.0]


<a id="v0.78.2"></a>
# [Release v0.78.2: BugFix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.78.2) - 2023-09-26

Bug fixes for the playback crashing due to video player calls in the wrong thread

[Changes][v0.78.2]


<a id="v0.78.1"></a>
# [Release v0.78.1: BugFixes and Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.78.1) - 2023-09-25

[Updated translations for cs/de/sv](https://github.com/vitorpamplona/amethyst/commit/e1caf40c3aa49c42c1db3b1356be21d223928f1c)
[Updating dependencies](https://github.com/vitorpamplona/amethyst/commit/d966541ea7c954e63e5d1c5e78b650c8a2cc8d2c)
[Forces the presence of the name field in kind 1](https://github.com/vitorpamplona/amethyst/commit/74a2b465084b5af237faf5aa0700a4e7c4186ff0)
[Disable swipe to change page in the hope page](https://github.com/vitorpamplona/amethyst/commit/045699ecd9af273c16b3213e521331025f75fece)
[Performance Improvements to PreviewCards](https://github.com/vitorpamplona/amethyst/commit/35f0b1291fa4bdfd70bfe418a2b94bbc35580094)
[Avoids creating nip94 and nip95 events with blank alts](https://github.com/vitorpamplona/amethyst/commit/f2b6a9bedb61a2c9ccffbabe7d6dd6102c130a70)
[Adds NIP19 parser to the AccountViewModel thread scope](https://github.com/vitorpamplona/amethyst/commit/fc4433e7ae91a05fe206d8b6c1492ced50545986)
[fix contact link in relay information dialog](https://github.com/vitorpamplona/amethyst/commit/b5a0d65f3c37bfdf2d21fc2834ed65a2b24a96c9)
[add notifications for users of amber](https://github.com/vitorpamplona/amethyst/commit/52f600485b16421613c5e3cedb422c6c7de33200)
[moves mark as read to the AccountViewModel scope.](https://github.com/vitorpamplona/amethyst/commit/e89e8e5d010d4d4a9aaea5312aec82e5f0ee6380)
[Fix for not clearing the new post message. It adds a delay to allow the composer to save the viewModel after calling cancel](https://github.com/vitorpamplona/amethyst/commit/af9b0b444c154682497f37100548ad3b63b12ea8)
[Improving the Stability of VideoView](https://github.com/vitorpamplona/amethyst/commit/b1edf4e934fa08dbce8e77be210de81986df75cd)
[only updates reports for a different note](https://github.com/vitorpamplona/amethyst/commit/288d80d1632e687465f1a59685f6d971567ae82b)
[Increasing video cache from 90 to 150MB](https://github.com/vitorpamplona/amethyst/commit/69d7e82b7106e45fbf8fe77c6c4b317cb4c4cade)

[Changes][v0.78.1]


<a id="v0.78.0"></a>
# [Release v0.78.0 Mute words](https://github.com/vitorpamplona/amethyst/releases/tag/v0.78.0) - 2023-09-24

- [Mute keywords](https://github.com/vitorpamplona/amethyst/commit/f4da2ae6be21fcda40bfa499d14d67cf65202290)
- [Adds an option in Settings to disable immersive scrolling](https://github.com/vitorpamplona/amethyst/commit/c6c15c3ec7c36f2c6d7ec502e860e4a1bc8c2447)
- [Only show backup keys if account has a private key](https://github.com/vitorpamplona/amethyst/commit/b3f39434eed8b8fb46f75f62780bc66174580514)
- [Reverts to have videos starting from the IO thread](https://github.com/vitorpamplona/amethyst/commit/57dfe3af8c03a3ba3a475060df278f3de26dc1a1)
- BugFix for restarting the relay filters when switching accounts after Amber's integration.

[Changes][v0.78.0]


<a id="v0.77.8"></a>
# [Release v0.77.8 BugFixes and Translation Updates](https://github.com/vitorpamplona/amethyst/releases/tag/v0.77.8) - 2023-09-22

- Filters zap payments outside the established min-max parameters on polls.
- Moves Live streaming video from the top bar to the body of the screen to avoid cancelling the video on immersive scrolling
- Reduces padding of custom emojis
- Refactors markdown parser away from the compose Class
- Fixes inconsistencies in memory management with the LifeCycle object and DispableEffect
- Speeds up the display of unused hashtags
- Moves video playback creation to the main thread
- Fixes profile website URLs without schema (http://, https://, etc)
- Updated translations for cs/de/sv by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Fix crash when editing account with Amber by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Updates pt-br translations by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Updates to Chinese and Hungarian translations by [@npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7](https://github.com/npub1raspu6ag9kfcw9jz0rz4z693qwmqe5sx6jdhhuvkwz5zy8rygztqnwfhd7) and [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)

[Changes][v0.77.8]


<a id="v0.77.7"></a>
# [Release v0.77.7: Immersive scrolling](https://github.com/vitorpamplona/amethyst/releases/tag/v0.77.7) - 2023-09-21

- Animates top and bottom bars out of the way when scrolling by [@greenart7c3](https://github.com/greenart7c3)
- Removes the need for the split payment popup for only one invoice
- Removes the flickering on chat screens when the bottom bar disappears
- Fixes video keeps playing in the background bug when pressing the bottom bar twice
- Moves reaction calls to the viewModelScope
- Protects against crashing on null contact lists
- Migrates alt descriptions of NIP-94 and NIP-95 from .content to alt tag
- Adds a new line limit to the char limit of the Show More button by [@davotoula](https://github.com/davotoula)

[Changes][v0.77.7]


<a id="v0.77.5"></a>
# [Release v0.77.5 Hides Nav and Top Bar when scrolling](https://github.com/vitorpamplona/amethyst/releases/tag/v0.77.5) - 2023-09-21



[Changes][v0.77.5]


<a id="v0.77.3"></a>
# [Release v0.77.3: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.77.3) - 2023-09-20



[Changes][v0.77.3]


<a id="v0.77.1"></a>
# [Release v0.77.1: Communities, hashtags, and geotags in the Top Bar list](https://github.com/vitorpamplona/amethyst/releases/tag/v0.77.1) - 2023-09-20

- Adds communities, hashtags, and geohashes to the lists on the top navigation bar.
- New Repost profile picture arrangement from [@npub1aeh2zw4elewy5682lxc6xnlqzjnxksq303gwu2npfaxd49vmde6qcq4nwx](https://github.com/npub1aeh2zw4elewy5682lxc6xnlqzjnxksq303gwu2npfaxd49vmde6qcq4nwx)
- Adds a Zap Split view to the master node in the Thread View
- Change nav bar color by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Removes the old user migration from the old preferences database
- Sets up default reactions to be Rocket, Hugs, Watching and Laughing
- Starting a Refactoring of LocalCache away from a Singleton instance
- Fixes blank summaries occupying empty spaces in Long Form content
- Improvements to the rendering of long-form content
- Groups notifications by day first to avoid merging multi-day notification events
- Performance improvements on loading users and calculating reports
- Disable buttons if terms are not accepted in the Login Screen with Amber by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Fixes a crash when login with Amber by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Updates Bengali and Dutch translations
- Removes the follow/unfollow button from the Zap to User view
- Performance improvement by moving zap decryption to run in a group, avoiding multiple co-routines per zap
- Updates Google Play services

[Changes][v0.77.1]


<a id="v0.77.0"></a>
# [Release v0.77.0: Amber support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.77.0) - 2023-09-18

Merging Amber branch

[Changes][v0.77.0]


<a id="v0.76.2"></a>
# [Release v0.76.2: Bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.76.2) - 2023-09-18

- [Removes the ZapSplit display when the weights are zero or were incorrectly created](https://github.com/vitorpamplona/amethyst/commit/83be43e94efdbe44adac188a8d624520ae9fec14)
- [Adds NIP24 Kind1 create function](https://github.com/vitorpamplona/amethyst/commit/4d1a99d0769d6ebe4550590ecaf01d30f96d0146)
- [AnimatedVisibility seems faster than Crossfade](https://github.com/vitorpamplona/amethyst/commit/b1debd98796a871b6f4e741e9eb494f2f8f47ba9)
- [Makes sure the account is writeable before signing Auth for notifications](https://github.com/vitorpamplona/amethyst/commit/3843917bd1300f89bc5a3b37ff9cc282264b0a37)
- Bengali, French, and Dutch translation updates.

[Changes][v0.76.2]


<a id="v0.76.1"></a>
# [Release v0.76.1 Zap Splits](https://github.com/vitorpamplona/amethyst/releases/tag/v0.76.1) - 2023-09-15

Adds support for Zap Split setup and payment via NWC and manually via wallet calls.

- Adds zap split setup
- Adds zap split display
- Adds zap split payment
- Fixes bug when verifying the checksum of LNURLs (bech32) in uppercase
- Allows playback to save any position in real-time and not only after 5 seconds
- Removes potential bugs when the list contains an e tag with an a reference
- Moves deletion and report to be the last item on the dropdown menu
- Moves broadcast up in the dropdown menu
- Updating dependencies
- Updated translations for cs/de/sv
- Forces valid hexes in p- and e- tags when receiving the event
- Stops accepting space as a Valid hex char and requires an even number of chars (padding)
- Forces the ZapRequest to exist when processing the LnZapEvent
- Fixes some long NIP-19 cutting off in notes
- Updates to Finnish, Swedish, Czech and German translations
- Adds Bengali, Bangladesh translations
- Adds Italian Translations
- Updates Chinese Translations

[Changes][v0.76.1]


<a id="v0.76.0"></a>
# [Release v0.76.0: GiftWrapped Push Notifications](https://github.com/vitorpamplona/amethyst/releases/tag/v0.76.0) - 2023-09-08

- Migrates to Encrypted Push Notifications
- BugFix with video thumbnails failing to load when using the Android's new Photo Picker
- Improvements to the relay settings UI.
- BugFix for invalid LNURLs highlighting as a link
- Uses the correct observer for the picture profile in the Top bar.
- Adds nostr.wine and noswhere.com as two new NIP-50 relays in the default list.
- Adds SeenOn relays when successfully broadcasting a note.
- Refines relay icon row compose
- Fixes the missing channel header for Live Activity replies in Conversations.
- Upgrades the RelayPoolStatus to a Flow
- Avoids race condition when updating EOSEs
- Adds arm64-v8a and armeabi-v7a build target
- Adds Indonesian translation
- Updates Czech, German, Swahili, Chinese, and Italian translations

[Changes][v0.76.0]


<a id="v0.75.14"></a>
# [Release v0.75.14: Fixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.14) - 2023-09-02

- [Fixes thread ordering issues with two notes with the same idHex](https://github.com/vitorpamplona/amethyst/commit/d13e8bacec4eff952a058eeea0ff9e277c96ec35)
- [Improves error message from LN Invoice servers.](https://github.com/vitorpamplona/amethyst/commit/a9ea9ea2aeac5966ce51db035611c1e34f3c9244)
- Error msg grammar adjustment
- [Adds UI Feedback for Custom Zaps](https://github.com/vitorpamplona/amethyst/commit/734dd2e119cc60c968085220040c572efdc93e3d)
- [Remeber should be based on the Note](https://github.com/vitorpamplona/amethyst/commit/d3fa05a4dfd2559aba0ee4fb1f067b635a97fa16)

[Changes][v0.75.14]


<a id="v0.75.13"></a>
# [Release v0.75.13](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.13) - 2023-09-01

- Ignores brb.io if in the relay list to reduce crashes at the WebSocket protocol level
- Only trigger notifications for events within 5minutes from the current time (avoids old notifications when rebroadcasting events)
- Identifies which relays are using zlib compression
- Doesn't notify if the GiftWrap's inner message came from the logged-in user.
- Fixes reply order for two replies made at the exact same second
- Fixes unit tests of thread ordering

[Changes][v0.75.13]


<a id="v0.75.12"></a>
# [Release v0.75.12: Global Feed bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.12) - 2023-08-31

- Fixes global feed
- Removes LiveData Redirections

[Changes][v0.75.12]


<a id="v0.75.11"></a>
# [Release v0.75.11: New Thread sorting scheme](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.11) - 2023-08-31

Replies on each thread level are now ordered by:
1. Parent-level reply author, ascending order by date.
2. My replies, descending by date.
3. My follow's replies, descending by date.
4. Everybody else, descending by date.

- Removes Zaps without a valid ZapRequest
- Removes unnecessary refresh checks since the dual LiveData structure refactoring
- Fixes wrong EOSE updates.
- Requires minimum 10 sat zaps to trigger notifications
- New Crowdin translations by GitHub Action
- Fixes the grammar in the nip05 error messages
- Improve cohesiveness of the pruning system.
- Reduce the padding space between display name and status in the left drawer
- Reducing the height of the banner in the drawer in the left drawer
- Fixes the report card when the author has been blocked by the loggedin user but the feed has been loaded before the block user list.
- Marks keepScreenOn to false when leaving the video player without pausing the video

[Changes][v0.75.11]


<a id="v0.75.10"></a>
# [Release v0.75.10](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.10) - 2023-08-29

- Fixes app crash when creating note with @ and any url on Samsungs
- Fixes link highligthing off when composing a message with @ and urls at the same time
- Refines zap and report memory pruning procedures
- Fix the mis-deletion of reports
- Lots of new translations from Crowdin collaborators (Thank you all!)

[Changes][v0.75.10]


<a id="v0.75.8"></a>
# [Release v0.75.8: Status Rotation bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.8) - 2023-08-27

- [clears the index cache when changing status fields.](https://github.com/vitorpamplona/amethyst/commit/6c54082a125b3059b0e7dd7bdb4ea9c9bd79c8a0)
- [Do a warning log and not an error log for verification fails](https://github.com/vitorpamplona/amethyst/commit/e1738a25a1244ee81d652a777941a4b72a454bd7)

[Changes][v0.75.8]


<a id="v0.75.7"></a>
# [Release v0.75.7: Quick Fix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.7) - 2023-08-27

- Fixes for a crash when receveing a new notification before other logged in accounts get loaded in memory

[Changes][v0.75.7]


<a id="v0.75.6"></a>
# [Release v0.75.6: Improved Error Handling for Zaps](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.6) - 2023-08-27

- Better error handling screens when Zaps fail
- Fixes an app crash when a user has more than 1 status
- Fixes All Follows filter for Live Activities.
- Additional Hungarian translation by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)
- Restructures status filters to minimize duplications.
- Adds polls to the hashtag screen
- Improves User filters by merging lastEOSEs into a single filter
- Faster crossfade animation between navigation screens
- Update French translations by [@anthony-robin](https://github.com/anthony-robin)
- Separates observer cleanup from memory pruning on the app pause
- Migrates to a double-layer observer structure for the memory cache
- Removes prefix filters
- Restructures channel data sources to avoid rebuilding the channel object

[Changes][v0.75.6]


<a id="v0.75.3"></a>
# [Release v0.75.3: 1 sat 1 vote polls](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.3) - 2023-08-25

- Activates 1 sat, 1 vote (min/max zap values) for polls
- BugFix for deleting statuses
- Moves isAcceptableNote calculations to viewModel
- Moves coroutine of NIP-05 verification to the viewModel
- Moves zap amount/deryption calculations to a viewModel
- Moves tallie calculations to a viewModel
- Adds a Toast message when no wallet is found to pay the invoice
- BugFix: starts the wallet app in a separate task to allow people to come back to Amethyst by switching apps instead of the back button
- Reduces the icon to the music and adds space before the status
- Adds the keyboard done action to be used as Post in the status field
- BugFix: Moves the User filter invalidation to the right channel
- Updates some dependencies
- BugFix: Adds some padding between the status and the clickable icon
- BugFix: for reloading events that have been deleted crashing the app
- Improves blinking issues when loading a thread.
- Fixes r-tag search in statuses
- BugFix for serialization of the per-relay since attributes in the event filter

[Changes][v0.75.3]


<a id="v0.75.2"></a>
# [Release v0.75.2: BugFix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.2) - 2023-08-24

Removes amethyst.social test code when creating new status updates

[Changes][v0.75.2]


<a id="v0.75.1"></a>
# [Release v0.75.1: Status updates](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.1) - 2023-08-24

https://cdn.nostr.build/i/c56a49811acd3a79a2b9b27d61c867d7132601157d6569f3d4d580446127d212.jpg

- Adds support for NIP-315: Status Updates
- Adds support for NIP-40: Expiration Timestamp, prunes events accordingly
- Only renders status if the nip05 checks pass
- Updates status on the Drawer
- Reduces font size for statuses and nip05s
- Animates a rotation of statuses if more than 1 is found
- Displays clickable links in the status row if they are present.
- Moves Manifest to target Android API 34
- Moves livedata creation to long-lived objects

[Changes][v0.75.1]


<a id="v0.75.0"></a>
# [Release v0.75.0 Replies and Boosts in NIP94/95, Lib Updates](https://github.com/vitorpamplona/amethyst/releases/tag/v0.75.0) - 2023-08-23

- Separates NIP24 implementation into the encryption NIP 44 and the messaging NIP 24
- Migrates to Compose 1.5.0, Updates Gradle plugin, Compose navigation, exoplayer, and vico dependencies to the latest.
- Saves some Notification Tab loading time by adding a check if a notification was already asked and rejected
- BugFix on bundled update dispatchers being on the wrong thread
- Faster daily reaction row compositions in the Notification tab
- Protects contact lists from pruning of all accounts in the device.
- Prunes events that are not from or cite one of the logged-in accounts
- BugFix Force Relay Reconnection when broadcasting new events.
- Small refactoring to create the PrivateZap Event for Quartz
- Activates pull to refresh on the Stories feed.
- Base support for Calendar events
- Migrates Addressable Events to a Base class on Quartz
- Refactors replaceable Event consumers into a single main function
- Activates pruning of old replaceable versions
- Adds replies and boosts buttons to Stories
- Adds Thai translation [@npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e](https://github.com/npub1vm0kq43djwdd4psjgdjgn9z6fm836c35dv7eg7x74z3n3ueq83jqhkxp8e)
- Removes Zap events from search.
- Corrects imePadding of the NewPostView
- Fixes quotes and boost previews for NIP94 and NIP95 content

[Changes][v0.75.0]


<a id="v0.74.5"></a>
# [Release v0.74.5: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.74.5) - 2023-08-21

- Fixes Thread's search for root to go backwards in the etag-stack.
- Fixes TopBar's and extended header's padding issues.
- Fixes the loading of GiftWraps URIs as intents in the app.
- Fixes a crash when not using play services with notifications by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)

[Changes][v0.74.5]


<a id="v0.74.4"></a>
# [Release v0.74.4: Coroutine Adjustments.](https://github.com/vitorpamplona/amethyst/releases/tag/v0.74.4) - 2023-08-21

- Fixes TopBar's and extended header's padding issues.
- Adds user info to the header of single-user chat rooms
- Breaks User + Created At UI line into two
- Moves community description and rules texts to a single Translateable text
- Fixes the copy to clipboard note ID of a GiftWrap message
- Fixes the feed refresh after adding an image on stories
- Fixes the divider presence in the Discover and Notification tabs
- Filter follow lists with the entire list of participants for Live Activities
- Uses KIND3 follows for the discovery tab notification dot
- Fixes issue with unfollowing hashtags written in a different case
- Minimizes the creation of new coroutine scopes
- Moves coroutines of BundledUpdate and BundledInsert to a managed model.
- Broadcasts all replied, quoted, and mentioned events together with the main event.

[Changes][v0.74.4]


<a id="v0.74.3"></a>
# [Release v0.74.3: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.74.3) - 2023-08-20

Adds Copy URL and Copy Note ID popups for images and videos for Gigi
BugFix for empty global feeds
BugFix to re-broadcast GiftWraps
BugFix to include zap events signed by the user's main key in the summary chart
BugFix for Playback services taking too long to start on Samsung phones
Updates media control dependencies to the latest version

[Changes][v0.74.3]


<a id="v0.74.2"></a>
# [Release v0.74.2: JSON Parser migration, UI Improvements, StemStr, new modules](https://github.com/vitorpamplona/amethyst/releases/tag/v0.74.2) - 2023-08-19

- Adds support for Stemstr's kind 1808
- Adds a waveform visual to audio playback when available
- Creates a Quartz Module for Nostr Events
- Creates a Benchmark Module to test Performance
- Migrates from GSon to Jackson for Performance gains, adapts all serializers accordingly
- Automatically activates NIP24 chats when users have received NIP24 messages from the counterparty
- Implements contextual rounded corners for images and videos
- Caches NIP-44 shared key for performance.
- Improves BechUtils encoding performance.
- Recreates Hex encoding/decoding classes for Performance.
- Migrates NIP24 to the new ByteArray concat encoding.
- Fixes Zap Notifications when the Zap payer is the logged-in user
- Fixes ThreadAssembler when there are two roots to a conversation branch
- Fixes the color of the historical chart to follow the chosen theme in settings
- Fixes chatroom names when clients send the same user twice in the p-tags.
- Removes support for lenient choices in the. events.
- Refactors TLV's, Events, and NIP-19 dependencies.
- Adds a large set of events as a test case for signature validation
- Adds new translations for cs/de/se by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Fixes url regex pattern for URLs with a dash by [@npub1l60d6h2uvdwa9yq0r7r2suhgrnsadcst6nsx2j03xwhxhu2cjyascejxe5](https://github.com/npub1l60d6h2uvdwa9yq0r7r2suhgrnsadcst6nsx2j03xwhxhu2cjyascejxe5)
- Fixes image uploading tests without an account
- Adds account info to image uploads test suites
- Moves navigation's top bar to use the Arrow Back UI Concept
- Trims display names when possible
- Don't display the username if the display name is available.
- Moves Relay List to a composable surface
- Migrates the use of pubkey prefixes in filters from 6 to 8 because more relays seem to work with this
- Adds read support for NIP-65
- Adds UI Improvements to the Settings Interface
- BugFix: Highlight event builder using the wrong kind
- BugFix: Avoids displaying a NIP-94 event without a url tag
- Updates SDK and dependencies to the latest

[Changes][v0.74.2]


<a id="v0.73.3"></a>
# [Release v0.73.3: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.73.3) - 2023-08-13

- BugFix for the blank screen when pressing the message button on a User Profile without any history of Chats
- BugFix for the crashing route when clicking in the GiftWrap DM Notification
- BugFix: Correctly updates Hashtags on a note after a new follow/unfollow
- Moves Zap Amount calculator from Compose scope to ViewModel Scope
- Avoids account state recompositions
- Moves Show And Hide functions from the Compose Scope to ViewModel Scope
- Moves the creation of the TopBar live data for lists into the AccountViewModel
- Moves HTTP and EMAIL regex compilation to a singleton

[Changes][v0.73.3]


<a id="v0.73.2"></a>
# [Release v0.73.2: PushNotifications for GiftWraps](https://github.com/vitorpamplona/amethyst/releases/tag/v0.73.2) - 2023-08-12

- Adds PushNotification for all GiftWrapped Events
- Fix: Marking All GiftWraped DMs as read
- Fix: Displays GiftWraped DM as Messages in the Notification Screen
- Fix: Menu drawer half open when switching to landscape mode by [@npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k](https://github.com/npub1a3tx8wcrt789skl6gg7rqwj4wey0j53eesr4z6asd4h4jwrd62jq0wkq4k)
- Fix: Forces the creation of Notification Channels to enable toggles in the Settings of Samsung phones
- Fix: Correctly displaying/hiding pictures in Chat Compose

[Changes][v0.73.2]


<a id="v0.73.1"></a>
# [Release v0.73.1: Private DMs and Group Chats](https://github.com/vitorpamplona/amethyst/releases/tag/v0.73.1) - 2023-08-12

This is an **alpha** release of the new GiftWrapped DMs. Please do not consider anything to be private until we have a stable version. This is early and there might be bugs that leak information.

This implementation is very similar to how Slack manages direct DMs to multiple users. If three users are having a conversation and want to add a forth person, the forth's user will not see the past. This guarantees maximum privacy: only the receivers of a message at the time of writting will ever be able to decrypt it.

The claims of this new method are:
- Messages are encrypted with a superior XChaCha algorithm to each participant's public key individually.
- Chat participant identities, each message's real date and time, event kinds, and other tags are all hidden from the public.
- Senders and receivers cannot be linked with public information alone.
- Minimal trust in counterparties: Counterparties cannot expose verifiable details of your message, including the metadata, without exposing their entire user and all of their other messages (private key)
- There is no central queue, channel or otherwise converging event id to link or count all messages in the same group.
- There is no moderation role (i.e. no group admins, no invitations or bans)
- There is no chatroom secret that can leak or be mistakently shared
- Messages can be fully recoverable in any client (that implements NIP-24) with the receiver or the sender's private key
- The protocol's messages can flow through public relays without loss of privacy. Private relays can increase privacy further, but they are not needed.
- The protocol is extensible to make any other event kind fully private (private likes, private reports, private long-form content, etc)

In the near future, we will implement Forward Secrecy
- Users will be able to opt-in for "Disappearing Messages" that are not recoverable with their private key
- Users will be able to also opt-in to sharing messages with a new key exclusive for DM backup and recovery.

You can activate this mode by clicking in the Incognito icon on the Chat screen. For now, only Amethyst supports this NIP. Thus we recommend only testing with other Amethyst users. Coracle and 0xChat are finishing their implementations in the upcoming days/weeks.

- Support for NIP-24 Private Messages and Small Groups
- Support for NIP-59 Gift Wraps & Seals
- Support for NIP-44 Versioned Encrypted Payloads
- Support for XChaCha encryption algorithm
- Fix: Loading of Alby's NWC URI
- Fix: Only requests notification permission once.
- Fix: Show reposts and reactions in search
- Fix: Signed byte used for array slice inside the TLV by [@npub1xpuz4qerklyck9evtg40wgrthq5rce2mumwuuygnxcg6q02lz9ms275ams](https://github.com/npub1xpuz4qerklyck9evtg40wgrthq5rce2mumwuuygnxcg6q02lz9ms275ams)
- Fix: Global feed only shows events from Global-active relays by [@npub10npj3gydmv40m70ehemmal6vsdyfl7tewgvz043g54p0x23y0s8qzztl5h](https://github.com/npub10npj3gydmv40m70ehemmal6vsdyfl7tewgvz043g54p0x23y0s8qzztl5h)
- Updates Dutch translations by [@npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd](https://github.com/npub1w4la29u3zv09r6crx5u8yxax0ffxgekzdm2egzjkjckef7xc83fs0ftxcd)

[Changes][v0.73.1]


<a id="v0.72.2"></a>
# [Release v0.72.2 Small Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.72.2) - 2023-08-05

[TimeAgo refactoring](https://github.com/vitorpamplona/amethyst/commit/c2b72f1e45f5f28a8b2ef2e9a043581eb617e62e)
[Puts marked as read on chat to an IO thread](https://github.com/vitorpamplona/amethyst/commit/28e6471adeef567bbbe93716bc17b946cc7d87eb)
[Puts an invalidate data on an IO thread](https://github.com/vitorpamplona/amethyst/commit/c3b8eb508724365cc6484530f30334408b394b9b)
[simple refactoring](https://github.com/vitorpamplona/amethyst/commit/75779e7ddc8dc74315b88ca115be7e94f66764b2)
[Don't invalidate data on channels if it is already invalidating](https://github.com/vitorpamplona/amethyst/commit/118bc7b73c30d25258d334b7e0956a77e9f13c0b)
[Removes unnecessary signing step](https://github.com/vitorpamplona/amethyst/commit/e6a0fdedd9bd78fb3e1057d687f7454cd6d78ea4)
[Don't push to invalidate unless there is an observer.](https://github.com/vitorpamplona/amethyst/commit/28098fafd7c3403bd85414ef70ab59d7b48d3aa6)
[Moves headers to the top nav bar](https://github.com/vitorpamplona/amethyst/commit/0c0e87af21617939933992d17af728f23c09d998)
[Removes the need for .0 when on base sats.](https://github.com/vitorpamplona/amethyst/commit/8abfd7149b62e2c1f9c68adc76fedacffde97331)
[Moves coroutine creation to the viewModel](https://github.com/vitorpamplona/amethyst/commit/674896cea468abe1c9ba24a3b7646b5da70c75c5)
[Makes the New Channel creation scrollable.](https://github.com/vitorpamplona/amethyst/commit/ec514651fcca8678462cf4bcf4afe8b8f7014ee1)
[updates github action to support minified versions of the app](https://github.com/vitorpamplona/amethyst/commit/8071d48911d452364b8ad620e243d2ad94a02b86)

[Changes][v0.72.2]


<a id="v0.72.1"></a>
# [Release v0.72.1: Caching Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.72.1) - 2023-08-04

- Expanded disk cache for images
- Moves NIP95 cache to internal/secure folders
- Fixes the interference between video and image caches
- Adds a cache forever tag to robohashes
- Moves URL Previews into suspend functions
- Adds a little border for Long Form Content
- Updated image upload API for nostr.build to v2 by [@npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8](https://github.com/npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8)
- Added support for NIP98 for nostr.build. Users with an account at nostr.build will upload files to their personal gallery through amethyst now by [@npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8](https://github.com/npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8)
- Updated French translations by [@anthony-robin](https://github.com/anthony-robin)
- Fixes new lines after images
- Fixes URL links containing "-"
- Displaying a bytecounter for events in memory when the app pauses.
- Slighly Faster picture galleries
- Avoids null image url errors that might be delaying some of the rendering.
- Uses Lists instead of Sets to reduce memory consumption of pointers.
- Moves onCreate intent evaluation to the App Navigation
- Moves Geolocation search to the IO thread
- Avoids using String.format due to an inner Synchronized block
- Uses a faster method to generate the hex of a url
- Moves to a minified release.

[Changes][v0.72.1]


<a id="v0.72.0"></a>
# [Release v0.72.0 New Memory Management](https://github.com/vitorpamplona/amethyst/releases/tag/v0.72.0) - 2023-08-01

- Adds aggressive memory management to avoid Out of Memory
- Fixes the App losing the back stack of screens
- Interns event strings to avoid duplicating memory with hashes and pubkeys
- Improves search by looking into all tags of all events
- Improves Tamil Translations by @
- Adds missing translations cs/de/se by [@npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef](https://github.com/npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef)
- Adds cryptographic and base event support for NIP-24 (GiftWraps and Sealed Gossip)
- Increases confidence requirements for the Translator
- Refactors the event creation into a Factory
- Adds new kinds to the hashtag and geotag search feeds
- Fixes the explainer text of the geohash addon
- Updates to the latest libraries
- Removes leakcanary due to irrelevance
- Cleans up unused values in strings
- Fix: Ignores past version of addressable content in feeds
- Fix: Avoids showing community definition types in the community feed.
- Fix: Avoids downloading 1000s of Nostr Connect events that some clients are generating.

[Changes][v0.72.0]


<a id="v0.71.0"></a>
# [Release v0.71.0 Geohash](https://github.com/vitorpamplona/amethyst/releases/tag/v0.71.0) - 2023-07-25

- Adds support for the `g` tag with a precision of 5x5km.
- Adds support for the following locations
- Adds support for multiple locations in lists
- Refactors New Post Buttons to make them more similar to one another
- Improves Translation-skip Indexes
- Reviews closing of response.body calls
- Refactors unused elements in VideoView
- Refactors Relay class to remove the Synchronized block
- Improves reachability of the relay screen when the keyboard is visible
- Fixes memory leaks when playback services are destroyed
- Fixes video release leak when double clicking on the Stories tab
- Fixes a crash when the video playback service isn't ready

[Changes][v0.71.0]


<a id="v0.70.8"></a>
# [Release v0.70.8: Permission Request test](https://github.com/vitorpamplona/amethyst/releases/tag/v0.70.8) - 2023-07-24

- Adds notification permissions request for Android Tiramisu

[Changes][v0.70.8]


<a id="v0.70.7"></a>
# [Release v0.70.7: Coding is hard](https://github.com/vitorpamplona/amethyst/releases/tag/v0.70.7) - 2023-07-24

- Bug fix for encrypting new private messages
- Bug fix for the back button going back in the stack instead of leaving the app

[Changes][v0.70.7]


<a id="v0.70.6"></a>
# [Release v0.70.6: Refactoring and improvements (Don't use this version)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.70.6) - 2023-07-24

- Fixes a crash when onNewIntent is called before onCreate
- New Post, Relay Choice: Select/Deselect all options by [@greenart7c3](https://github.com/greenart7c3)
- New Post, Relay Choice: Fixes missing switch when url is too long by [@greenart7c3](https://github.com/greenart7c3)
- Adds missing OptIn when using GlobalScope by [@greenart7c3](https://github.com/greenart7c3)
- Refactors Crypto/Hex/Bech classes and dependencies
- Simplifies relay connection status
- Moves to OkHttpClient on URL Preview Queries
- Moves to OkHttpClient on Image uploads
- Refactoring of the Connectivity Settings
- Avoids crossfading animations when loading NIP94 and NIP95 content
- Moves playback service startup to the IO Thread
- Activates Strict mode in debug
- Updates Firebase version
- Updates Hungarian translations by [@ZsZolee](https://github.com/ZsZolee)

[Changes][v0.70.6]


<a id="v0.70.5"></a>
# [Release v0.70.5: UI Bugfixes and improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.70.5) - 2023-07-22

[Releases the video player on Mutex when destroying the app.](https://github.com/vitorpamplona/amethyst/commit/8a12cc3cce58346773f9cd5e1643258b8db14355)
[Releases the Mutex when stopping the feed.](https://github.com/vitorpamplona/amethyst/commit/7e03870c0be640a5b5dc48855826c5c8feb41ee4)
[Fixes lightning colors](https://github.com/vitorpamplona/amethyst/commit/a6f56416e3f3394401c4221175d9001ba4896774)
[Fixes color of the NIP05 address in the profile](https://github.com/vitorpamplona/amethyst/commit/efe7772135dbb318508da0bc04fff26f8e8c2816)
[Removes extra padding in the video of a live activity mention](https://github.com/vitorpamplona/amethyst/commit/15c96ce00728afd5c7dc8410cd66c697354366bd)

[Changes][v0.70.5]


<a id="v0.70.4"></a>
# [Release v0.70.4: Wakelock Leak](https://github.com/vitorpamplona/amethyst/releases/tag/v0.70.4) - 2023-07-21

[Moves wake lock to activate only when the video is actually playing](https://github.com/vitorpamplona/amethyst/commit/2664292993e731012ec351a823cd1203aafbaff5)

[Changes][v0.70.4]


<a id="v0.70.3"></a>
# [Release v0.70.3: Bugfixes and improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.70.3) - 2023-07-21

- Fix KeepPlayingButton color in light theme by [@greenart7c3](https://github.com/greenart7c3)
- Adds a space when rendering inline images and url previews for [@npub1aeh2zw4elewy5682lxc6xnlqzjnxksq303gwu2npfaxd49vmde6qcq4nwx](https://github.com/npub1aeh2zw4elewy5682lxc6xnlqzjnxksq303gwu2npfaxd49vmde6qcq4nwx)
- [Moves the loading of an Accounts backup contacts to the IO Thread](https://github.com/vitorpamplona/amethyst/commit/5cdceb51942a8ecc8497c1a2e841ba411ba22fd6)
- [Adjustments to Modifiers in the Chatroom screen](https://github.com/vitorpamplona/amethyst/commit/2c82e6c44731b139fca52bc403519e7bcbecf751)
- [Refactors ChatroomHeader compose](https://github.com/vitorpamplona/amethyst/commit/9a517380a0447223c88346e28dffc23983b5c48b)

[Changes][v0.70.3]


<a id="v0.70.2"></a>
# [Release v0.70.2: Bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.70.2) - 2023-07-21

- Updated se/de/cs translations by [@davotoula](https://github.com/davotoula)
- Moves the video caching service initialization foreground services.
- Fix boosted notes from blocked users appearing as blank by [@greenart7c3](https://github.com/greenart7c3)

[Changes][v0.70.2]


<a id="v0.70.1"></a>
# [Release v0.70.1: BugFix for F-Droid](https://github.com/vitorpamplona/amethyst/releases/tag/v0.70.1) - 2023-07-21

- Restructures the spacing in the first and second rows of a post
- Fixes Wake-Lock permission line that crashes on the F-droid version

[Changes][v0.70.1]


<a id="v0.70.0"></a>
# [Release v0.70.0: Background Playback](https://github.com/vitorpamplona/amethyst/releases/tag/v0.70.0) - 2023-07-20

- Moves Video/Audio player to a foreground service.
- Migrates Feed, Stories, and Live Stream screens to use that service
- Blocks screen from going to sleep if a video is playing.
- Blocks WIFI from going to sleep if an online video is playing.
- Allows the app to pause while listening to media and continue playing
- Manages cache for up to 30 videos in parallel for each of the 3 categories: local, streaming, progressive content
- Activates the use of popups with artwork that points to the screen with the video
- Creates a button to allow any video to play while browsing the app/phone
- Moves app to SingleTop mode.
- Keeps viewed position cached for up to 100 videos.
- Restructures the starting screen from App Navigation

[Changes][v0.70.0]


<a id="v0.69.3"></a>
# [Release v0.69.3: BugFix NIP05 colors](https://github.com/vitorpamplona/amethyst/releases/tag/v0.69.3) - 2023-07-17



[Changes][v0.69.3]


<a id="v0.69.2"></a>
# [Release v0.69.2: Updated Verification icons](https://github.com/vitorpamplona/amethyst/releases/tag/v0.69.2) - 2023-07-17

Adds [@Niel Liesmons](https://snort.social/p/npub149p5act9a5qm9p47elp8w8h3wpwn2d7s2xecw2ygnrxqp4wgsklq9g722q)'s icons for Following, Nostr Address Verification, and NIP-94 hash verification

[Changes][v0.69.2]


<a id="v0.69.1"></a>
# [Release v0.69.1: BugFix to view the mute feed](https://github.com/vitorpamplona/amethyst/releases/tag/v0.69.1) - 2023-07-17



[Changes][v0.69.1]


<a id="v0.69.0"></a>
# [Release v0.69.0: Ad-hoc local lists removed](https://github.com/vitorpamplona/amethyst/releases/tag/v0.69.0) - 2023-07-16

This version starts the migration to store a user's following communities, channels, and block/mute list in the Contact List and People List kinds. To migrate, please block (not report) and unblock somebody, follow and unfollow somebody.

- Moves video player to the brand new Exoplayer package
- Moves Following Communities' local DB to the Contact List
- Moves Following Channels local DB to Contact List
- Moves Following BlockList local DB to Mute List (Private part)
- Migrates all past local lists to their event kinds
- Views Mute Feed (disables hidden authors for that specific list)
- Breaks Security Filters screen in 2 tabs: Blocked and Spammers (automated filter)
- Restructures ContactList to avoid removing unsupported tags
- Restructures PeopleList to avoid removing non-people tags
- Fixes older channels and communities not loading on the discovery tab
- Forces a given event kind to be displayed in the Discovery tab
- BugFix for AppDefinitionEvent updates
- Moves contact list closer to metadata in the Local Cache
- Adds parsing support for NNS Events.
- Moves id hex prefixes from 6 to 8 chars.
- Fixes the profile display for new users without pictures.

[Changes][v0.69.0]


<a id="v0.68.4"></a>
# [Release v0.68.4: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.68.4) - 2023-07-14

- Renames NIP-05 to Nostr Address for [@derekross](https://github.com/derekross)
- Moves UserProfile NewThreads, Conversations, Reports and App Recommendations to be additive filters
- Moves the synchronized Zap allocation block to run only when needed
- Moves badges out of the User class
- Keeping media feed active from the start of the app.
- Only logging pruning and printing stacktraces when it matters
- Adds EOSE limits to the Video tab
- Adds EOSE limits to the Discovery tab.
- Only triggers mutable state of connection if the connection actually changes.
- Reduces profile feed size to 200 and zaps, reports and followers to 400
- Puts the Wifi signal processing in an IO Thread

Download:
- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.68.4/amethyst-googleplay-universal-v0.68.4.apk)
- [F-Droid Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.68.4/amethyst-fdroid-universal-v0.68.4.apk)

[Changes][v0.68.4]


<a id="v0.68.3"></a>
# [Release v0.68.3: Bug fixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.68.3) - 2023-07-14

- Adds [NostrCheck](https://nostrcheck.me/) uploads on the Media Feed
- Adds the title of the blog post to the [highlighter's](https://highlighter.com/) display
- Fixes size of custom reactions on the Stories feed
- Fixes crash when changing setting in android api < 13 by [@greenart7c3](https://github.com/greenart7c3)
- Migrates DropDownMenu to MutableState for performance
- Changes clicks on Community and Channel headers to go to the community/channel screen instead of expanding it inline
- Update Japanese translations by [@akiomik](https://github.com/akiomik)
- Update Portuguese translations by [@greenart7c3](https://github.com/greenart7c3)

[Changes][v0.68.3]


<a id="v0.68.2"></a>
# [Release v0.68.2: Bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.68.2) - 2023-07-13

- [Fixing Right To Left Text](https://github.com/vitorpamplona/amethyst/commit/0d9399a1a45da32b86c043fa5323866bcd040b18)
- BugFix for Aways show images/videos in the dialog screen

[Changes][v0.68.2]


<a id="v0.68.1"></a>
# [Release v0.68.1: Settings Page](https://github.com/vitorpamplona/amethyst/releases/tag/v0.68.1) - 2023-07-13

- Adds Settings for (by [@greenart7c3](https://github.com/greenart7c3))
  - The default language of the app
  - The default theme of the app
  - If it loads images automatically or not
  - If it loads URL previews automatically or not
  - If it plays videos automatically or not
- Updates to the Japanese translations by [@akiomik](https://github.com/akiomik)

[Changes][v0.68.1]


<a id="v0.68.0"></a>
# [Release v0.68.0 Classifieds](https://github.com/vitorpamplona/amethyst/releases/tag/v0.68.0) - 2023-07-13

- Adds Support for Classifieds ([nostr-protocol/nips#662](https://github.com/nostr-protocol/nips/pull/662))
- Adds Japanese Translations by [@npub1f5uuywemqwlejj2d7he6zjw8jz9wr0r5z6q8lhttxj333ph24cjsymjmug](https://github.com/npub1f5uuywemqwlejj2d7he6zjw8jz9wr0r5z6q8lhttxj333ph24cjsymjmug)
- BugFix to cut multiple emojis from reactions
- BugFix to remove emoji package list selection from Profile threads.

[Changes][v0.68.0]


<a id="v0.67.1"></a>
# [Release v0.67.1: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.67.1) - 2023-07-12

- When clicking in a repost of a note, redirects the user to the note directly, not to the repost
- Adjustments to allow removing of the selected emoji packs from the reaction setup dialog


[Changes][v0.67.1]


<a id="v0.67.0"></a>
# [Release v0.67.0: Custom Emoji Reactions](https://github.com/vitorpamplona/amethyst/releases/tag/v0.67.0) - 2023-07-12

- Support for Emoji Packs
- Support for Personal Emoji Lists
- Support for Custom emoji Reactions

[Changes][v0.67.0]


<a id="v0.66.7"></a>
# [Release v0.66.7: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.66.7) - 2023-07-12

- Fixes race condition when deleting a reposted event
- Fixes a crash when a lightning service returns an emtpy pr field.
- Fixes images going over text in a markdown rendering
- Early support for displaying custom emoji reactions
- Updates exoplayer version
- Makes nav(route) threaded operation
- Refactoring of Badge Box codes and Time classes
- Adds rendering of community approval posts to the NoteMaster of a thread
- Updates Tamil translations by [@AutumnSunshine](https://github.com/AutumnSunshine)

[Changes][v0.66.7]


<a id="v0.66.6"></a>
# [Release v0.66.6: BugFix for content-warning checkbox not working](https://github.com/vitorpamplona/amethyst/releases/tag/v0.66.6) - 2023-07-09



[Changes][v0.66.6]


<a id="v0.66.5"></a>
# [Release v0.66.5: Content-Warning in the video feed](https://github.com/vitorpamplona/amethyst/releases/tag/v0.66.5) - 2023-07-09

- Adds content-warning toggle when sending an image.
- Toggle marks the media event (NIP-94 or NIP-95) as content-sensitive
- Change renderers in chat, communities, feed, and stores to show a warning before the image is displayed.

[Changes][v0.66.5]


<a id="v0.66.4"></a>
# [Release v0.66.4](https://github.com/vitorpamplona/amethyst/releases/tag/v0.66.4) - 2023-07-08



[Changes][v0.66.4]


<a id="v0.66.3"></a>
# [Release v0.66.3: Live Notification Bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.66.3) - 2023-07-07

- Avoids triggering the Live Notification bubble when the live event is simply updated with new participants
- Adds content-sensitivity warnings for LiveStreams
- Fixes translations from Japanese with special chars breaking the url
- Adds hashtags to the description of communities and channels
- Displays community/channel descriptions with Translations.
- Slightly faster reactions and zap icon rendering

[Changes][v0.66.3]


<a id="v0.66.2"></a>
# [Release v0.66.2: BugFixes for Crashes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.66.2) - 2023-07-07

[Creates a fallback for Android9 on OPPO, which doesn't seem able to parse the Japanese regex.](https://github.com/vitorpamplona/amethyst/commit/c93a6dffc4dc56eaee28689af85d1cb915ea8968)
[BugFix for invalid URLs crashing the app.](https://github.com/vitorpamplona/amethyst/commit/60dadc008877de62927c490ed32066e30958c75e)
[Fixes caching of time display.](https://github.com/vitorpamplona/amethyst/commit/a26c7e3d9777210dae4d84a59de0f7cb06b5ae4c)

[Changes][v0.66.2]


<a id="v0.66.1"></a>
# [Release v0.66.1: Community/Chat headers with reactions/zaps](https://github.com/vitorpamplona/amethyst/releases/tag/v0.66.1) - 2023-07-07

- Moves communities from /c/name to /n/name
- Adds reactions to owners of Chats and Communities
- Adds zaps reactions to owners of Chats and Communities
- Fixes Emoji combinations for
- Reduces the size of the discovery tab captions
- Increases the most recent downloads per chat from 25 to 50
- Removes top bar for Community and Hashtag posts
- Adds EOSE treatment for the Discovery datasource
- Allows notification to chat channel headers

[Changes][v0.66.1]


<a id="v0.66.0"></a>
# [Release v0.66.0: Communities (NIP 172)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.66.0) - 2023-07-06

- Breaks the live screen in 3 tabs
- Adds Support for Communities (172)
- Adds Community discovery screen
- Adds Public Chat discovery screen
- Adds Community Follow/Unfollow
- Adds rendering of Community Posts in the feed
- Creates a summary of verified participants in Communities/Chats/Streams
- Restructures Hashtag Screen to the new Screen Building structure
- Remembers scroll position in Live, Community, and Chat discovery
- Displays Approval Notes in the notifications
- Removes the Live bar from the top of the feed
- Adds notification dots in the LiveStream bottom button.
- Updated Swedish/Czech/German translations by [@davotoula](https://github.com/davotoula)
- BugFix for invalid hexes in the hex index
- BugFix for Japanese Url parsing and Text newline breaks by [@ShinoharaTa](https://github.com/ShinoharaTa)

[Changes][v0.66.0]


<a id="v0.65.1"></a>
# [Release v0.65.1: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.65.1) - 2023-07-05

- [Performance updates](https://github.com/vitorpamplona/amethyst/commit/f78ec5cc90e6232531e1322f9161111289eb35a9)
- [Fixes](https://github.com/vitorpamplona/amethyst/commit/fd6a63a76c51ab72b1698d99c1e70c7d82ef36fa) [#487](https://github.com/vitorpamplona/amethyst/issues/487)
- [BugFix for not rendering images on markdown when there is no NIP19 reference in the text.](https://github.com/vitorpamplona/amethyst/commit/e0f186bb94063068d27d14b93b463b66dd79a5bd)

[Changes][v0.65.1]


<a id="v0.65.0"></a>
# [Release v0.65.0: Live Streaming Browser](https://github.com/vitorpamplona/amethyst/releases/tag/v0.65.0) - 2023-07-04

- Live Streaming Browser

[Changes][v0.65.0]


<a id="v0.64.4"></a>
# [Release v0.64.4: BugFix for chat delay](https://github.com/vitorpamplona/amethyst/releases/tag/v0.64.4) - 2023-07-03

- [BugFix for delayed update of the last messages in the Messages screen](https://github.com/vitorpamplona/amethyst/commit/91c47b6e87495dd5f540842579ac396efe8e0c4a)

[Changes][v0.64.4]


<a id="v0.64.3"></a>
# [Release v0.64.3: New icon for verified follows](https://github.com/vitorpamplona/amethyst/releases/tag/v0.64.3) - 2023-07-02

- BugFix for IndexOutOfBoundsException when loading invalid hexes
- Adds New Icon for verified follows
- Fixes the lack of the checkmark in the verification icons
- Adds #Cashu custom tag by [@npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8](https://github.com/npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8)

[Changes][v0.64.3]


<a id="v0.64.2"></a>
# [Release v0.64.2: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.64.2) - 2023-07-01

- [Shows channel picture on the public chat channel's header instead of the creator's picture](https://github.com/vitorpamplona/amethyst/commit/5a3ea1c25824c2bc3b3f9a5d00eb13f9c325cb84)
- [Adds a Toast message for not having a private key when following/unfollowing](https://github.com/vitorpamplona/amethyst/commit/e4020817775d7e2e215cf22e6e7579a58798c8d7)
- [BugFix for not changing background color on Notes](https://github.com/vitorpamplona/amethyst/commit/ed9c27341f925feb9e3d47d86c0f5b896a42d48c)
- [Performance Improvements: Moves away from `drawBehind` and uses background color instead](https://github.com/vitorpamplona/amethyst/commit/56d9c9a50fbc39177f92931cf14dffdffc2c4c07)
- [Removes the need to draw a background in the verification symbol](https://github.com/vitorpamplona/amethyst/commit/09582fd0b13ec6893d7fbaa9ef944bdd037f4694)
- [Reduces the max grouped reactions on Notifications to 30](https://github.com/vitorpamplona/amethyst/commit/1d5dfbfd29aad9d36813f14be4a75332f3a94018)
- [Moves Note/User updates from 300ms to 500ms](https://github.com/vitorpamplona/amethyst/commit/cd9465c0e755b92d34475afae4d4a2dfb4f02107)
- [New RichText engine to help with testing classes.](https://github.com/vitorpamplona/amethyst/commit/d179c93c0bd19a5e6ba59c8c1a19085641440216)
- [fix relay tab not working in profile page](https://github.com/vitorpamplona/amethyst/commit/53a146d0e54a8b0f33f4869625447b2da6c5894a)
- [BugFix Crashing relay screen on invalid URLs](https://github.com/vitorpamplona/amethyst/commit/b820b6564f5812c28888398b93fdbd577e47f912)
- [Reduces mentions to hex in the UI](https://github.com/vitorpamplona/amethyst/commit/3908c42c7fa940e5f9a5236f6a2c4a4f2f2acce7)

[Changes][v0.64.2]


<a id="v0.64.0"></a>
# [Release v0.64.0: UI Changes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.64.0) - 2023-06-29

- Merges Global into Home as a top-bar List
- Moves Relay Settings info from top bar to left drawer
- Adds search to the top bar
- Slims down the live headers
- Additional checks for main thread in Notifications
- Makes NIP05 work with domain names only (No need for the _@ in the "_@ domain" address)
- Improved AsyncImage loading states
- Logs out the time to display each reaction type
- BugFix: Not showing display name on [n] tags
- BugFix: Trimmed paragraphs losing indentation
- BugFix: Don't show URL previews in quoted notifications and other cases where the preview is not needed.

[Changes][v0.64.0]


<a id="v0.63.0"></a>
# [Release v0.63.0: Zitron's Logo!](https://github.com/vitorpamplona/amethyst/releases/tag/v0.63.0) - 2023-06-29

- Moves to [@npub103vypyhddrad9289zp8lf2dxlkkrmq3e0utx3qg449ea8x2wel6sas2700](https://github.com/npub103vypyhddrad9289zp8lf2dxlkkrmq3e0utx3qg449ea8x2wel6sas2700) 's Logo
- Adds followed tags in profile screen by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Adds music note to #tunestr

[Changes][v0.63.0]


<a id="v0.62.8"></a>
# [Release v0.62.8: More Performance](https://github.com/vitorpamplona/amethyst/releases/tag/v0.62.8) - 2023-06-28

#Amethyst v0.62.8: More Performance

- Performance improvements and additional MainThread checks
- Logging rendering performance
- BugFix for rendering url of highlight note by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Add copy token button to Cashu's Preview by [@npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8](https://github.com/npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8)
- BugFix better default error message by [@npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8](https://github.com/npub1nxa4tywfz9nqp7z9zp7nr7d4nchhclsf58lcqt5y782rmf2hefjquaa6q8)
- Runs translation chain in the background
- Restructures Multi-Notification Galleries
- BugFix: Avoids blinking the nip05 address
- BugFix: Ignoring case when comparing original and translated versions.
- Moves RichText/Markdown Modifiers to singleton classes.
- Updates URLPreview in the main thread.
- Updates dependencies to the newest version
- Updates Hungarian translation by [@npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp](https://github.com/npub1ww8kjxz2akn82qptdpl7glywnchhkx3x04hez3d3rye397turrhssenvtp)


Download:
- Play Edition: https://github.com/vitorpamplona/amethyst/releases/download/v0.62.8/amethyst-googleplay-universal-v0.62.8.apk
- F-Droid Edition: https://github.com/vitorpamplona/amethyst/releases/download/v0.62.8/amethyst-fdroid-universal-v0.62.8.apk

[Changes][v0.62.8]


<a id="v0.62.7"></a>
# [Release v0.62.7: BugFix for zapraiser](https://github.com/vitorpamplona/amethyst/releases/tag/v0.62.7) - 2023-06-27

- Fixes the large UI padding in the details of the zapraiser

[Changes][v0.62.7]


<a id="v0.62.6"></a>
# [Release v0.62.6: Perf improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.62.6) - 2023-06-27

- Moves state assignments to the main thread
- Reduces full-screen updates to once a second
- Minor adjustments in threading
- Moves cashu processing to a state class to account for errors

[Changes][v0.62.6]


<a id="v0.62.5"></a>
# [Release v0.62.5: Bugfix for crashing on invalid cashu token](https://github.com/vitorpamplona/amethyst/releases/tag/v0.62.5) - 2023-06-27

- [Minor adjustments in UI threading](https://github.com/vitorpamplona/amethyst/commit/52af109b4eb8ea91716de96090693d584b32e2c4)
- [Moves cashu processing to a state class to account for errors.](https://github.com/vitorpamplona/amethyst/commit/833e6bc3e14534a396a320fe6fa1f0663f3a06b5)

[Changes][v0.62.5]


<a id="v0.62.4"></a>
# [Release v0.62.4: Cashu Token support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.62.4) - 2023-06-26

- Adds Cashu Token redemption to the User's ln address by [@believethehype](https://github.com/believethehype)
- Video Dialog now go full screen by [@vitorpamplona](https://github.com/vitorpamplona)
- Updates Dutch Translations by [@Bardesss](https://github.com/Bardesss)
- Adds a confirmation dialog before logout by [@greenart7c3](https://github.com/greenart7c3)
- Bugfix for not updating user picture and name after a new message arrives on the Message List by [@vitorpamplona](https://github.com/vitorpamplona)
- Several little performance Improvements across the app by [@vitorpamplona](https://github.com/vitorpamplona)
- Adjusts Profile images to crop and not fit by [@vitorpamplona](https://github.com/vitorpamplona)
- Preloads channel data from LocalCache by [@vitorpamplona](https://github.com/vitorpamplona)
- Removes live thumb and adjusts padding of the live header [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.62.4]


<a id="v0.62.3"></a>
# [Release v0.62.3: BugFixes and Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.62.3) - 2023-06-25

- Adds picture of the User to the Stream's header
- Migrates the UserProfile page to the new ViewModel structure
- Requires an update in the live stream event every two hours to keep live
- Adds a less memory-intensive Hex Checker
- Moves OnlineChecker to a singleton class with a cache
- Moves LocalCache's Synchronized block to Concurrent implementations
- Moves user msg synchronization to its own object
- Crossfades changes in Reactions and Zaps
- Improves the rendering speed of chat messages
- BugFix for new posts by the user not showing up immediately in the feed
- BugFix for duplicated notes in the LocalCache
- BugFix for the Click on UserPicture event

[Changes][v0.62.3]


<a id="v0.62.2"></a>
# [Release v0.62.2: BugFix for crashing relay screen](https://github.com/vitorpamplona/amethyst/releases/tag/v0.62.2) - 2023-06-23

- Some relays don't return a JSON when requested and that was crashing the app.

[Changes][v0.62.2]


<a id="v0.62.1"></a>
# [Release v0.62.1: BugFix for posts with createdAt in the future](https://github.com/vitorpamplona/amethyst/releases/tag/v0.62.1) - 2023-06-23

- Stops showing posts in the future (Fixes Tony's Highlighter post)
- BugFix on publishedAt date for Longform posts

[Changes][v0.62.1]


<a id="v0.62.0"></a>
# [Release v0.62.0 NIP-98 Support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.62.0) - 2023-06-23

- Adds NIP98 Support by [@believethehype](https://github.com/believethehype)
- Adds nostrcheck.me image host by [@believethehype](https://github.com/believethehype)
- Fixes UTF-32 Reactions cutting off
- Adds icon for paid relays in the relay list by [@greenart7c3](https://github.com/greenart7c3)
- Improves rendering time of the Channel header
- Avoids crashing due to illegal zap address
- Making more classes stable for performance reasons.
- Improves relay options rendering performance.
- Review to avoid Synchronized blocks
- Uses derivedOf to protect from updates with the same content
- Generalizes the SensitivityWarning to any Note
- Moves LaunchedEffects to only run if objects haven't yet being loaded
- Checks main thread in preferences
- Moves the image border modifiers to app variable

[Changes][v0.62.0]


<a id="v0.61.4"></a>
# [Release v0.61.4: Relay Info pages](https://github.com/vitorpamplona/amethyst/releases/tag/v0.61.4) - 2023-06-21

- Adds internal Relay Info pages when clicking on relay icons by [@greenart7c3](https://github.com/greenart7c3)
- Removes old image proxy classes by [@vitorpamplona](https://github.com/vitorpamplona)
- Improves rendering performance of Chat screens and messages by [@vitorpamplona](https://github.com/vitorpamplona)
- Updates Hungarian translation for Zapraiser by [@ZsZolee](https://github.com/ZsZolee)

[Changes][v0.61.4]


<a id="v0.61.3"></a>
# [Release v0.61.3 Too many Live Streams Bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.61.3) - 2023-06-20

- BugFix for too many streams (max = 2 for now)
- BugFix for opening the video chat with the video in full screen until it loads.
- Fix margins when the Live Activity summary is not available
- Refreshing stream list when follows change, including Live activities.

[Changes][v0.61.3]


<a id="v0.61.2"></a>
# [Release v0.61.2: Live streaming in the chat](https://github.com/vitorpamplona/amethyst/releases/tag/v0.61.2) - 2023-06-20

- Restructures chat message rendering
- Adds sensitive content warning to chats
- Adds live activities to the main feed
- Adds live activities chats to the conversations tab
- Adds public chat msgs to the conversations tab

[Changes][v0.61.2]


<a id="v0.61.1"></a>
# [Release v0.61.1: Screen diet](https://github.com/vitorpamplona/amethyst/releases/tag/v0.61.1) - 2023-06-20

- Improvements in the use of space of UI elements
- Moves the streaming label to the bottom of the video
- Removes the top bar for streaming and chats
- Adds options to react and zap the author of the streaming
- BugFix for streaming rotation of videos
- BugFix for not updating Notifications quickly

[Changes][v0.61.1]


<a id="v0.61.0"></a>
# [Release v0.61.0: Live streaming chats](https://github.com/vitorpamplona/amethyst/releases/tag/v0.61.0) - 2023-06-19

- Support for Live Chat event kind (1311)
- Renders Channel headers, NIP94 and NIP95 in the thread's master post
- Add toast message to show relay icon description by [@greenart7c3](https://github.com/greenart7c3)
- BugFix for notifications over very old posts not appearing

[Changes][v0.61.0]


<a id="v0.60.2"></a>
# [Release v0.60.2: Scroll to The top BugFix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.60.2) - 2023-06-19

- Restructures feed invalidations to account for changes in the selected top list
- Restructures scroll-to-the-top implementations to avoid using arguments in the navigator
- Restructures Stable elements for minor performance gains.

[Changes][v0.60.2]


<a id="v0.60.1"></a>
# [Release v0.60.1: Per-Post Zapraisers](https://github.com/vitorpamplona/amethyst/releases/tag/v0.60.1) - 2023-06-18

Allows users to add an amount of sats to raise per post.
Displays zap raisers as a progress bar to completion in the post.

[Changes][v0.60.1]


<a id="v0.60.0"></a>
# [Release v0.60.0: Live Activities & Generic Repost support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.60.0) - 2023-06-18

- Basic Support for live activities (NIP-102)
- Adds support for Kind 16 (Generic Repost)
- Activates support for m3u8 streaming
- Migrates `e` citations to replaceable events to their latest version
- Migrates last-seen times saved per route to account management
- Removes original tags from Reposts
- BugFix on restarting the video when pressing mute
- BugFix to not scroll the feed to the top when the new follow list is updated
- BugFix for a background color for messages in notifications
- Increases performance of the follow/follower count

[Changes][v0.60.0]


<a id="v0.59.1"></a>
# [Release v0.59.1: Name Playback](https://github.com/vitorpamplona/amethyst/releases/tag/v0.59.1) - 2023-06-16

https://nostr.build/av/e99f5528e0f18946b171b36da852e7470f84d13956a37fa4dcaae89c010d5d35.mp4

- BugFix for Messages being marked as read from Notifications.
- Text to speech for usernames and display names
- Removes some duplicated Boosts
- BugFix for feed not updating after change in follows.
- BugFix for some reposts not showing up in the feed.

[Changes][v0.59.1]


<a id="v0.58.3"></a>
# [Release v0.58.3: Custom Reaction bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.58.3) - 2023-06-15

- BugFixes for the size of emojis on the Video feed.
- BugFixes for the alignment of emojis on the feed of some phones.
- BugFix for deleting a reaction and going back to the Red Heart image

[Changes][v0.58.3]


<a id="v0.58.2"></a>
# [Release v0.58.2: Like always active Bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.58.2) - 2023-06-15



[Changes][v0.58.2]


<a id="v0.58.1"></a>
# [Release v0.58.1: Bug Fix for long reaction comments](https://github.com/vitorpamplona/amethyst/releases/tag/v0.58.1) - 2023-06-15



[Changes][v0.58.1]


<a id="v0.58.0"></a>
# [Release v0.58.0: Custom reactions](https://github.com/vitorpamplona/amethyst/releases/tag/v0.58.0) - 2023-06-14

- Custom reactions
- Starts to preload objects before the feed is ready
- Simplifies URL Preview calls
- Hungarian translation updates by [@ZsZolee](https://github.com/ZsZolee)

[Changes][v0.58.0]


<a id="v0.57.0"></a>
# [Release v0.57.0: Animations for loading posts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.57.0) - 2023-06-13

- Uses animateItemPlacement to show new Notes and update the size of current ones as they load
- Adds NIP-10 markers to kind 1s, fixing bug with thread view in other clients
- New Privacy Policy for F-droid
- Adds an option to opt-out from automatic spam and report filters by [@npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5](https://github.com/npub1w4uswmv6lu9yel005l3qgheysmr7tk9uvwluddznju3nuxalevvs2d0jr5)
- Adjusts the size and alignment of relay icons in the video feed
- Refactors some of the Zap Request,Response objects in the interface for performance
- BugFix for emtpy space in Notications when blocked posts are included.
- Updates kotlin dependencies
- Activates build cache

[Changes][v0.57.0]


<a id="v0.56.5"></a>
# [Release v0.56.5: Support for NIP-14 (Subject Line)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.56.5) - 2023-06-11

- Basic Support for displaying NIP-14 on notes (Subject)
- BugFix for Video Uploads when the video is already compressed to the minimum
- Improves markdown support
- Fixing upstream library issue with images on markdown
- Refactors color objects to avoid recreating them on-demand

[Changes][v0.56.5]


<a id="v0.56.4"></a>
# [Release v0.56.4: BugFix for a race condition when starting the app](https://github.com/vitorpamplona/amethyst/releases/tag/v0.56.4) - 2023-06-09



[Changes][v0.56.4]


<a id="v0.56.3"></a>
# [Release v0.56.3: Reconnecting after network drop](https://github.com/vitorpamplona/amethyst/releases/tag/v0.56.3) - 2023-06-09

- Adds a network listener to reconnect when connectivity comes back
- BugFix for duplicated push notifications
- Moves background color changes to IO Threads with Mutable State
- Breaks down the Note Composition stack further
- Fixes border/spacing issues between multiple note types
- Aligns Quick Actions to the center of the note.
- Improves the speed of the highlighter rendering
- Refactoring all shapes to the appropriate file

[Changes][v0.56.3]


<a id="v0.56.2"></a>
# [Release v0.56.2: BugFixes and Adjustments](https://github.com/vitorpamplona/amethyst/releases/tag/v0.56.2) - 2023-06-08

- Moves channel's sending message from the main thread to the IO thread
- Removes duplicated event-id protections from Notifications
- BugFix for notifications sometimes not reassembling when pressing the bottom button.
- Adds a second rule for short messages in the spam filter to avoid recognizing "@ bot, command" messages spam.
- Moves the creation of the VideoPlayer to its own composition
- Adds #onyx custom tag by [@TonyGiorgio](https://github.com/TonyGiorgio)
- Updates Hungarian translation by [@ZsZolee](https://github.com/ZsZolee)

[Changes][v0.56.2]


<a id="v0.56.1"></a>
# [Release v0.56.1: Compressed Uploads](https://github.com/vitorpamplona/amethyst/releases/tag/v0.56.1) - 2023-06-08

- Adds Video Compression on upload
- Adds Image Compression on upload (GIFs not supported yet)
- Adds #weedsstr custom tag from Onyx/[@TonyGiorgio](https://github.com/TonyGiorgio)

[Changes][v0.56.1]


<a id="v0.56.0"></a>
# [Release v0.56.0: Per-post reaction display](https://github.com/vitorpamplona/amethyst/releases/tag/v0.56.0) - 2023-06-07



[Changes][v0.56.0]


<a id="v0.55.4"></a>
# [Release v0.55.4: Support for Relay Set Event Type](https://github.com/vitorpamplona/amethyst/releases/tag/v0.55.4) - 2023-06-07



[Changes][v0.55.4]


<a id="v0.55.3"></a>
# [Release v0.55.3: Refactoring Messages Screen](https://github.com/vitorpamplona/amethyst/releases/tag/v0.55.3) - 2023-06-07



[Changes][v0.55.3]


<a id="v0.55.2"></a>
# [Release v0.55.2: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.55.2) - 2023-06-05



[Changes][v0.55.2]


<a id="v0.55.1"></a>
# [Release v0.55.1: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.55.1) - 2023-06-05



[Changes][v0.55.1]


<a id="v0.55.0"></a>
# [Release v0.55.0: NIP-89 App Recommendation Systems](https://github.com/vitorpamplona/amethyst/releases/tag/v0.55.0) - 2023-06-03

- Displays NIP-89 Notes in the feed
- Displays recommendations on profile
- Displays NIP-89-related notifications for your apps.
- BugFix: Making sure video does not restart when pressing the mute button
- BugFix: Keeping the position of the feed in navigation

[Changes][v0.55.0]


<a id="v0.54.2"></a>
# [Release v0.54.2: More Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.54.2) - 2023-06-03

- Adds comment and amount of sats sent directly to the profile in Notifications
- Adds more caching capabilities in content parsing
- Restructures WordRenderer to help recompositions
- Improves performance of custom emojis
- Improves performance of DropDown menus
- Removing outdated version of the FlowRow
- Runs bottom's new notification refresher in the IO thread
- Only refreshes follow lists once per notification event
- Moves post's bounty calculations to the IO thread
- Moves profile DB watchers to their own compose functions
- Adds a new thread call for the notification of reaction rows
- Ignores unloaded notes in the MultiComposeSet renderer.
- Moves UserFeeds to ImmutableLists
- Moves reaction counts to a thread
- Moves channel checks to before a Channel object is loaded.

[Changes][v0.54.2]


<a id="v0.54.1"></a>
# [Release v0.54.1: More Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.54.1) - 2023-06-01

[Fixes Translations bug by previous version](https://github.com/vitorpamplona/amethyst/commit/b0e50e0c00f6cfe7cc4da234675aee281192c1bd)
[Restructuring observables](https://github.com/vitorpamplona/amethyst/commit/92c61f317bef860c0ab2e80ed4f6fb9e0160378c)

[Changes][v0.54.1]


<a id="v0.54.0"></a>
# [Release v0.54.0: More Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.54.0) - 2023-06-01

- [Add support for per-app languange preferences](https://github.com/vitorpamplona/amethyst/commit/a466f33b4e4f9fb420123ce2efb3da38026ab08d) by [@davotoula](https://github.com/davotoula)
- [Avoids notifying twice](https://github.com/vitorpamplona/amethyst/commit/1cc0f4bf80dc750bbace2b156fad2828982c938c)
- [Restructuring ViewModels](https://github.com/vitorpamplona/amethyst/commit/d942c126277d9737c4d67b75b6e341915ca83887)
- [Improves Poll Rendering speed.](https://github.com/vitorpamplona/amethyst/commit/3eb832c4e09cdfa9bd2d33bef74ad904e99f58f2)
- [Reducing recompositions on images.](https://github.com/vitorpamplona/amethyst/commit/6072e5977e8a0d9a95b53004f54ce8812dcfdda9)
- [Improves rendering performance of the ExoPlayer](https://github.com/vitorpamplona/amethyst/commit/91f34000f535230ae198747d90cbaaf2995ffc4c)

[Changes][v0.54.0]


<a id="v0.53.7"></a>
# [Release v0.53.7: Zap, Reply, etc to Reports](https://github.com/vitorpamplona/amethyst/releases/tag/v0.53.7) - 2023-05-30



[Changes][v0.53.7]


<a id="v0.53.6"></a>
# [Release v0.53.6: Security Filter List bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.53.6) - 2023-05-30

[Sorting blocked users by name](https://github.com/vitorpamplona/amethyst/commit/030064e112ed9e5982bfd7b1e2f32c4943eaa634)
[BugFix for not loading the security filter screen](https://github.com/vitorpamplona/amethyst/commit/d9a392d2c1f3314e6d7ae277e7f4c09228e846ba)

[Changes][v0.53.6]


<a id="v0.53.5"></a>
# [Release v0.53.5: Videos over Tor bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.53.5) - 2023-05-30

[Restart Video Cache when chancing Tor Setup](https://github.com/vitorpamplona/amethyst/commit/24a6d8a155159884c451638ba614e741643333d5)

[Changes][v0.53.5]


<a id="v0.53.4"></a>
# [Release v0.53.4: Markdown bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.53.4) - 2023-05-30

- [Fixes bug with rendering of markdown with user citations.](https://github.com/vitorpamplona/amethyst/commit/6ea0972151b9237a4e15f7426b84d5c6f76b2836)
- [Improves rendering of Markdown summaries](https://github.com/vitorpamplona/amethyst/commit/f2db2b88c25595b3b47acdddc640f5d98ae089a1)

[Changes][v0.53.4]


<a id="v0.53.3"></a>
# [Release v0.53.3: Video Player with Tor Support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.53.3) - 2023-05-29

- [adds flavor to the version descriptor in the bottom left of the drawer](https://github.com/vitorpamplona/amethyst/commit/1972297755366bb94d29a2cc24b067756d6cab8e)
- [Moves ExoPlayer to OkHttp in order to enable Tor support.](https://github.com/vitorpamplona/amethyst/commit/ac006946907ef9bf13f8b005056384f7ad128b96)

[Changes][v0.53.3]


<a id="v0.53.2"></a>
# [Release v0.53.2: Message Notification bubble bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.53.2) - 2023-05-29

- [Correcting the user's room for the notification marker](https://github.com/vitorpamplona/amethyst/commit/43d508ba3366e0f2c205dc6a7d7903496a0d4a36)

[Changes][v0.53.2]


<a id="v0.53.1"></a>
# [Release v0.53.1: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.53.1) - 2023-05-29

[Filters Message Feed DAL to only take PrivateMessages in the Notification Bubble.](https://github.com/vitorpamplona/amethyst/commit/64f98e4b924e4fe99dee8d42270822ff3c1e19c0)
[Adding immutable tag to ResizeImage class](https://github.com/vitorpamplona/amethyst/commit/c58c20cc541ddd45b684c81d60ae0cbdea28d124)

[Changes][v0.53.1]


<a id="v0.53.0"></a>
# [Release v0.53.0](https://github.com/vitorpamplona/amethyst/releases/tag/v0.53.0) - 2023-05-29

[Updated Persian Translations by @malimahda](https://github.com/vitorpamplona/amethyst/commit/83df639aff159da7b547d7030f72e6f915975b4a) [#430](https://github.com/vitorpamplona/amethyst/pull/430)
[Bugfix case sensitive nip05 by @davotoula ](https://github.com/vitorpamplona/amethyst/commit/0f6bf1cc59c409e880fc8ab831e94723e00a6e8e) [#428](https://github.com/vitorpamplona/amethyst/pull/428)
[Adds separate colors for the newItem background between light and dark themes](https://github.com/vitorpamplona/amethyst/commit/cb92a51191469ad2c480c9bb382cd3bcf1974a00)
[Adds Czech translations by @davotoula](https://github.com/vitorpamplona/amethyst/commit/213b172c32f7ddbf3bdaf2ba917ba14d706cbac0) [#431](https://github.com/vitorpamplona/amethyst/pull/431)
[Moves chatroom list to an additive filter](https://github.com/vitorpamplona/amethyst/commit/a5a3c62edd75bfb36085d1f6b88264455354f3ad)
[Avoids running through the entire filter when collection is Empty](https://github.com/vitorpamplona/amethyst/commit/02ad85a740172b61508e16521cf64acd4127feef)
[Adds mute control per track on the screen.](https://github.com/vitorpamplona/amethyst/commit/446273de80498b665e0d1fb58bb19c20b5a9fd12)
[Optimizing UserProfile status](https://github.com/vitorpamplona/amethyst/commit/b513877552f28b256bc01ba4d49b3768478a1d2a)
[Refreshing user status when added to the transient spam list](https://github.com/vitorpamplona/amethyst/commit/1a477ea1c89dadbf3b94533c7207c25549b6f238)
[Adds some documentation to make sure any NIP05 changes take into account that we are converting the entire json to lowercase.](https://github.com/vitorpamplona/amethyst/commit/f298cc6f13a79234e8fa4dd437636b253f57d207)
[Loading reply from launch and not withContent](https://github.com/vitorpamplona/amethyst/commit/6bc1b6b28ab2d20b77f9a823f1f00f7854757afe)

[Changes][v0.53.0]


<a id="v0.52.3"></a>
# [Release v0.52.3: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.52.3) - 2023-05-28

[Moves all note states to leaf compositors](https://github.com/vitorpamplona/amethyst/commit/5e3703e849adcbf7c881db58a73de68680fd72ff)
[Better recomposition structure for ReactionsRow](https://github.com/vitorpamplona/amethyst/commit/acbd41ce61c8f5e7736c7346cf163139b06748f7)
[Performance Improvements when rendering a channel creation note](https://github.com/vitorpamplona/amethyst/commit/056d00b73b51b9cc467e3f06e15b6260d3b3ea32)

[Changes][v0.52.3]


<a id="v0.52.2"></a>
# [Release v0.52.2: Bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.52.2) - 2023-05-27

- Sensitive content Hungarian translation by [@ZsZolee](https://github.com/ZsZolee)
- Trims relay urls

[Changes][v0.52.2]


<a id="v0.52.1"></a>
# [Release v0.52.1](https://github.com/vitorpamplona/amethyst/releases/tag/v0.52.1) - 2023-05-27



[Changes][v0.52.1]


<a id="v0.52.0"></a>
# [Release v0.52.0 NIP-36 (Sensitive Content)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.52.0) - 2023-05-26



[Changes][v0.52.0]


<a id="v0.51.4"></a>
# [Release v0.51.4: Zap Amount contrast improvements in light mode](https://github.com/vitorpamplona/amethyst/releases/tag/v0.51.4) - 2023-05-26



[Changes][v0.51.4]


<a id="v0.51.3"></a>
# [Release v0.51.3: BugFix for changing lists not changing the feed](https://github.com/vitorpamplona/amethyst/releases/tag/v0.51.3) - 2023-05-26

Only changing stats for the Reaction Row if the data actually changed.
Improves rendering of messages in the Notification feed
Feed invalidation is necessary for lists
Simplifying Private message rendering scheme
Updates jsoup and activity compose to the latest versions

[Changes][v0.51.3]


<a id="v0.51.2"></a>
# [Release v0.51.2](https://github.com/vitorpamplona/amethyst/releases/tag/v0.51.2) - 2023-05-25

Changes the structure of the notification view to increase speed.
Preserves invalidations of DataSources for Home, Videos and Notificat…
Only removes scrollTop from Uri after 1 second and to avoid changing …
Activates invalidations that can be ignored if another invalidation i…
Only scrolls to the top if not at the top
Remembers formatting for the Notification Reaction Row
Updates Notification lists in intervals of 1 second.
Remember modifiers to avoid recomposition
Moves Home and Video screen invalidations to follow the scrollTop par…
Adds @ stable tag to Notification Card models
Remembering the size of the robohash picture
Removes logs
Removing the procedure to start connecting with relays before logging in.

[Changes][v0.51.2]


<a id="v0.51.1"></a>
# [Release v0.51.1](https://github.com/vitorpamplona/amethyst/releases/tag/v0.51.1) - 2023-05-24



[Changes][v0.51.1]


<a id="v0.51.0"></a>
# [Release v0.51.0](https://github.com/vitorpamplona/amethyst/releases/tag/v0.51.0) - 2023-05-24



[Changes][v0.51.0]


<a id="v0.50.6"></a>
# [Release v0.50.6: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.50.6) - 2023-05-23

- [Adds Immutable and Stable annotations to avoid recomposing](https://github.com/vitorpamplona/amethyst/commit/8898e5d10bf7797a92e08a6ca98ad4a5e173ca44)
- [Improves the speed of updating Notification notes](https://github.com/vitorpamplona/amethyst/commit/15621f16a2e6f945c5aab7aa7b896190b89c6c0b)
- [Adds a cache layer for citations in Notes](https://github.com/vitorpamplona/amethyst/commit/a29d9ad433afccbd04f8b9c0c51234336135845e)
- [Fixes click on the Home to update the feed](https://github.com/vitorpamplona/amethyst/commit/d355f22938b765acefca8cfb8452f711e38071b1)
- [Fixes size for following symbol](https://github.com/vitorpamplona/amethyst/commit/2082c6cb28bab865edbde715faeb29c1f8e1ecbe)
- [Adds Better translations to unfollow in Portuguese](https://github.com/vitorpamplona/amethyst/commit/4b49cc1aab15322df67bf774f6073b056928fbe6)
- [Updates dependencies](https://github.com/vitorpamplona/amethyst/commit/a1da4affeb5ef65cee66b7b94d4bfc7fd24699e6)
- [Moves more variables to remember clauses](https://github.com/vitorpamplona/amethyst/commit/0bf94f316f6b21d4eb132e89e661036cccd715c2)

[Changes][v0.50.6]


<a id="v0.50.5"></a>
# [Release v0.50.5](https://github.com/vitorpamplona/amethyst/releases/tag/v0.50.5) - 2023-05-22

- [Moves zapProgress to inside the IO update to make sure it only update…](https://github.com/vitorpamplona/amethyst/commit/6437a2106a8e0ac80322bc86f1d481de78e726e7)
- Stops recomposing the drawer at every new route.
- Adjusts bottombar to not recalculate hasNewItems in every recomposition.
- Moves default state of url previews to the remember function
- Remembering Background color and Text elements for posts.
- Optimizes MultiSetCompose rendering
- Optimizes recomposition of bottom items.
- Optimizes NIP-05 procedures
- Optimizes Drawer for Recomposition
- Removes the time log for Composing a note
- Only updates the refreshing state if it was refreshing.
- Deprecating IMGUR
- Updates relay UI on messages sent by the logged in user in chat
- Migrates notification summary to a new day without having to restart
- Apply fix to profile navigation of the same page  by [@KotlinGeekDev](https://github.com/KotlinGeekDev)
- Adds hashtags and #[n] rendering to markdown
- Fixes NWC with Auth
- [Adds nostr: prefix to channel id share.](https://github.com/vitorpamplona/amethyst/commit/330f3503a6961ef1e5c0fcdf55e751f8a41ba810)

[Changes][v0.50.5]


<a id="v0.50.4"></a>
# [Release v0.50.4](https://github.com/vitorpamplona/amethyst/releases/tag/v0.50.4) - 2023-05-17

[Bugfix for saving Replaceable notes in Bookmarks.](https://github.com/vitorpamplona/amethyst/commit/323e71c7cbbe27a1716f3c0a20d830866b3f1f02)
[Bugfix for channel metadata messages show up before channel creation](https://github.com/vitorpamplona/amethyst/commit/e41df98920764888199507378e35518eb1269d7e)

[Changes][v0.50.4]


<a id="v0.50.3"></a>
# [Release v0.50.3 BugFixes and Updates](https://github.com/vitorpamplona/amethyst/releases/tag/v0.50.3) - 2023-05-17

[Replacing some missing Hungarian translation](https://github.com/vitorpamplona/amethyst/commit/c5176b8c4973be9b2527aadcbbe674c68b6f62b5)
[Add lowercase variant of hashtag when creating event.](https://github.com/vitorpamplona/amethyst/commit/d6c8651f0d46ed205e48788bd4e26daeaa2837dc)
[Fixes Auth for NIP-42 sporadic connections.](https://github.com/vitorpamplona/amethyst/commit/5040350be5ff659d0682f859741061c6e5a224b8)

[Changes][v0.50.3]


<a id="v0.50.2"></a>
# [Release v0.50.2: Jumping chat post fix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.50.2) - 2023-05-17

[Fixes jumping chat screens on hidden posts.](https://github.com/vitorpamplona/amethyst/commit/117446688b0f9d4045a216fad1feaa51c8142b7a)

[Changes][v0.50.2]


<a id="v0.50.1"></a>
# [Release v0.50.1: Reaction Watch Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.50.1) - 2023-05-17

- UI and Performance improvements
- [Adds Navigation markers to the top panel and reaction watch](https://github.com/vitorpamplona/amethyst/commit/95a21cc08c161b138915c652d324402f404526af)
- BugFixes when switching accounts
- BugFix in the like count
- Better caching system for compiled stats

[Changes][v0.50.1]


<a id="v0.50.0"></a>
# [Release v0.50.0: Reaction Watch](https://github.com/vitorpamplona/amethyst/releases/tag/v0.50.0) - 2023-05-17



[Changes][v0.50.0]


<a id="v0.49.4"></a>
# [Release v0.49.4: Colored relay icons](https://github.com/vitorpamplona/amethyst/releases/tag/v0.49.4) - 2023-05-16

- [Adds online search when typing the username in the new Post field.](https://github.com/vitorpamplona/amethyst/commit/5a3cf4040210aefd7217e301ad93c7953635f9cc)
- [Increasing size of the "More Options" button in the Video Feed.](https://github.com/vitorpamplona/amethyst/commit/1519e70b24691d127b85f2521dc02ce2016b61d6)
- [Optimizes the code for chat bubble rendering](https://github.com/vitorpamplona/amethyst/commit/135596591a48766d266869dda0a3e4718aade8eb)
- [Fixes Robohash dependency in size](https://github.com/vitorpamplona/amethyst/commit/389afcb6003e31e3c8c39d400d1f459ae8e5322a)
- [Adds color to relay icons](https://github.com/vitorpamplona/amethyst/commit/d6c29667925428e1816dc2e83ead705a458d55b8)
- [Avoiding creating a lnurl link with just those words](https://github.com/vitorpamplona/amethyst/commit/dd8b208a0d6227592f51f123ecb754030784dae5)

[Changes][v0.49.4]


<a id="v0.49.3"></a>
# [Release v0.49.3: Search improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.49.3) - 2023-05-16

- [Improves rendering performance for chats.](https://github.com/vitorpamplona/amethyst/commit/3950517743aa396e9393cf98611708d1abe87c57)
- [Parsing NIP-19 on search to improve search results.](https://github.com/vitorpamplona/amethyst/commit/9e93a35ee7d16aed6b3c1d443d06cbe55a655695)
- [Making sure filters return similar orders when two posts are published by adding idHex as a sorting element](https://github.com/vitorpamplona/amethyst/commit/76df70c6f67880dd552c7671a3969686db83c1d3)

[Changes][v0.49.3]


<a id="v0.49.2"></a>
# [Release v0.49.2: Custom Emoji for Chats](https://github.com/vitorpamplona/amethyst/releases/tag/v0.49.2) - 2023-05-16

[Fixing emoji's on chat handles](https://github.com/vitorpamplona/amethyst/commit/f2d87b78b1b882f048ceb3efde7d02e6d8a1b6e4)

[Changes][v0.49.2]


<a id="v0.49.1"></a>
# [Release v0.49.1: Custom Emoji Bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.49.1) - 2023-05-16

- [Better shade for Pins](https://github.com/vitorpamplona/amethyst/commit/d29e6b4d0be69b8a18f683998914cd2ffec14c2d)
- [Avoid color-filtering the emoji](https://github.com/vitorpamplona/amethyst/commit/14e670cd9a953aeedf7690ae88e7621a3bd0577d)
- [Adjustments to Markdown detection and rendering.](https://github.com/vitorpamplona/amethyst/commit/332490376b7ca459cbd6170b3b82613b6f6942aa)
- [BugFix for incorrect caching of NIP95](https://github.com/vitorpamplona/amethyst/commit/17fbf8c01f6d555ce2b57d3c15576016ed7c69aa)

[Changes][v0.49.1]


<a id="v0.49.0"></a>
# [Release v0.49.0: Pinstr support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.49.0) - 2023-05-16



[Changes][v0.49.0]


<a id="v0.48.0"></a>
# [Release v0.48.0: NIP-30: Custom Emojis](https://github.com/vitorpamplona/amethyst/releases/tag/v0.48.0) - 2023-05-16

- Adds support for NIP-30 in Kind 0 (metadata) and Kind 1s (posts)
- Adds Swedish translations by [@Pextar](https://github.com/Pextar)
- Adds NIP-94 shareable link to filestr
- Adds zapstr.live and listr.lol as shareable links
- Adds a 5 minute expiration time for image loading errors
- Fixes NPE on opening the QuickAction on a "Post Not Found" event.

[Changes][v0.48.0]


<a id="v0.47.0"></a>
# [Release v0.47.0: Support for Zapstr.live (Audio Tracks)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.47.0) - 2023-05-15



[Changes][v0.47.0]


<a id="v0.46.6"></a>
# [Release v0.46.6](https://github.com/vitorpamplona/amethyst/releases/tag/v0.46.6) - 2023-05-15



[Changes][v0.46.6]


<a id="v0.46.5"></a>
# [Release v0.46.5](https://github.com/vitorpamplona/amethyst/releases/tag/v0.46.5) - 2023-05-15



[Changes][v0.46.5]


<a id="v0.46.4"></a>
# [Release v0.46.4](https://github.com/vitorpamplona/amethyst/releases/tag/v0.46.4) - 2023-05-14



[Changes][v0.46.4]


<a id="v0.46.3"></a>
# [Release v0.46.3](https://github.com/vitorpamplona/amethyst/releases/tag/v0.46.3) - 2023-05-14

- [Avoids Notification duplicates](https://github.com/vitorpamplona/amethyst/commit/cf9395d5e3827df2b65eaf498a018a166bc91d79)
- [BugFix for non-english hashtags] by [@vitorpamplona](https://github.com/vitorpamplona) ([`d261003217`](https://github.com/vitorpamplona/amethyst/commit/d2610032172bb26083d61ae2b8a09378b001d565)) by [@vivganes](https://github.com/vivganes)

[Changes][v0.46.3]


<a id="v0.46.2"></a>
# [Release v0.46.2](https://github.com/vitorpamplona/amethyst/releases/tag/v0.46.2) - 2023-05-14

- Improvements to Poll Caching system (Fixes: [#406](https://github.com/vitorpamplona/amethyst/issues/406))
- Performance Improvements to Follow Lists - Turn FollowList into a ViewModel
- Allows the RichTextViewer to render before resolving urls.
- BugFix: Protects against exceptions when creating keys with invalid bytearrays.
- BugFix: [Failing to load a video thumbnail](https://github.com/vitorpamplona/amethyst/commit/fc2408b526a4c74c6cbbace43b41774da36b0aaa)
- [French translation](https://github.com/vitorpamplona/amethyst/commit/672380653414520da6a9f2a87f8f9011408fe32b) by [@judemont](https://github.com/judemont)
- [TOR Hungarian translation](https://github.com/vitorpamplona/amethyst/commit/93c3a49ecf3e1952897c1891a35ef4ef65c1ed5f) by [@ZsZolee](https://github.com/ZsZolee)

[Changes][v0.46.2]


<a id="v0.46.1"></a>
# [Release v0.46.1: Notify buttons on reply](https://github.com/vitorpamplona/amethyst/releases/tag/v0.46.1) - 2023-05-11

- Displays People Lists as Notes in the Feed
- Improves reply screen to clarify which users will be notified
- Adds action button to join channels/users in the chat screen
- Isolates state changes in the profile page, avoiding recomposition
- General Performance Improvements

[Changes][v0.46.1]


<a id="v0.46.0"></a>
# [Release v0.46.0: Tor Support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.46.0) - 2023-05-11

- Tor/Orbot support by [@greenart7c3](https://github.com/greenart7c3)
- Added Farsi translations by [@malimahda](https://github.com/malimahda)
- Updated Ukrainian and Russian translations by [@Radiokot](https://github.com/Radiokot)
- Updated Dutch translations by [@Bardesss](https://github.com/Bardesss)

[Changes][v0.46.0]


<a id="v0.45.1"></a>
# [Release v0.45.1: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.45.1) - 2023-05-10

- [Fixing blank messages and Zapped DM content](https://github.com/vitorpamplona/amethyst/commit/c1d127ac1e251f37a72adec547f32692272ca977)
- [Don't try to decrypt if message is blank](https://github.com/vitorpamplona/amethyst/commit/434c1e9af324cbef64ea0a5c20e55e768c300219)
- [Capitalize notification sentence](https://github.com/vitorpamplona/amethyst/commit/0b2e5de7527384b4efef0d558269fdfdcb25f683)
- Performance Upgrades

[Changes][v0.45.1]


<a id="v0.45.0"></a>
# [Release v0.45.0](https://github.com/vitorpamplona/amethyst/releases/tag/v0.45.0) - 2023-05-10



[Changes][v0.45.0]


<a id="v0.44.0"></a>
# [Release v0.44.0: Lists to Notifications](https://github.com/vitorpamplona/amethyst/releases/tag/v0.44.0) - 2023-05-08

- [Adding List Choice to Notifications](https://github.com/vitorpamplona/amethyst/commit/04c13003170f452839e2eee73cd364ea33805e0f)
- [Fixing tab sliders from jumping from one state to the other](https://github.com/vitorpamplona/amethyst/commit/4dcf38c492ca1c9ef541dccfe044552224c737cd)
- [Moving follows to the known list in DMs](https://github.com/vitorpamplona/amethyst/commit/81290f2b26b1487f55302a612ab8f0eccd200227)
- Performance upgrades

[Changes][v0.44.0]


<a id="v0.43.2"></a>
# [Release v0.43.2: BugFix for NostrFiles.dev](https://github.com/vitorpamplona/amethyst/releases/tag/v0.43.2) - 2023-05-08



[Changes][v0.43.2]


<a id="v0.43.1"></a>
# [Release v0.43.1: NostrFiles.dev support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.43.1) - 2023-05-08



[Changes][v0.43.1]


<a id="v0.43.0"></a>
# [Release v0.43.0: Performance improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.43.0) - 2023-05-07



[Changes][v0.43.0]


<a id="v0.42.5"></a>
# [Release v0.42.5: Gradient removal](https://github.com/vitorpamplona/amethyst/releases/tag/v0.42.5) - 2023-05-05

- [Testing removing the gradient of quoted posts.](https://github.com/vitorpamplona/amethyst/commit/635279d59af22313d48a7d54509a9f66f5c04973)

- [BugFix for displaying other people's list after loading them from the…](https://github.com/vitorpamplona/amethyst/commit/0c673e91c6dd8f5930015d20bb58ecb10da15b19)

- [BugFix for the empty selection](https://github.com/vitorpamplona/amethyst/commit/3519e20a98d7c8191cf964a16a2310a774ea85ba)

[Changes][v0.42.5]


<a id="v0.42.4"></a>
# [Release v0.42.4: BugFix: Private Zap decrypt without a Private Key](https://github.com/vitorpamplona/amethyst/releases/tag/v0.42.4) - 2023-05-05



[Changes][v0.42.4]


<a id="v0.42.2"></a>
# [Release v0.42.2: Bookmarks bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.42.2) - 2023-05-05



[Changes][v0.42.2]


<a id="v0.42.1"></a>
# [Release v0.42.1: BugFix for migration of Account lists](https://github.com/vitorpamplona/amethyst/releases/tag/v0.42.1) - 2023-05-05



[Changes][v0.42.1]


<a id="v0.42.0"></a>
# [Release v0.42.0: Private & Public Follow Lists](https://github.com/vitorpamplona/amethyst/releases/tag/v0.42.0) - 2023-05-05

- Private & Public Follow Lists in the top bar by [@vitorpamplona](https://github.com/vitorpamplona)
- BugFix for garden hashtag by [@believethehype](https://github.com/believethehype)

[Changes][v0.42.0]


<a id="v0.41.0"></a>
# [Release v0.41.0: NFC, nostr.build, img proxy removal](https://github.com/vitorpamplona/amethyst/releases/tag/v0.41.0) - 2023-05-03

Amethyst 0.41.0 (alpha): NFC, Nostr.build uploader and Img Proxy removal test

- Adds nostr.build uploader by [@believethehype](https://github.com/believethehype)
- Adds NFC support for NIP19 URIs by [@vitorpamplona](https://github.com/vitorpamplona)
- Updates Dutch translations by [@Bardesss](https://github.com/Bardesss)
- Removes image proxy for profiles (testing) by [@vitorpamplona](https://github.com/vitorpamplona)
- BugFix for tall images bleeding into other posts on Stories by [@vitorpamplona](https://github.com/vitorpamplona)

Download:
- Play Edition with Translations: https://github.com/vitorpamplona/amethyst/releases/download/v0.41.0/amethyst-googleplay-universal-v0.41.0.apk
- F-Droid Edition without Translations: https://github.com/vitorpamplona/amethyst/releases/download/v0.41.0/amethyst-fdroid-universal-v0.41.0.apk

[Changes][v0.41.0]


<a id="v0.40.6"></a>
# [Release v0.40.6 Bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.40.6) - 2023-05-02

- BugFix to avoid blinking the url while loading images
- BugFix for drawing the border of the image without content
- BugFix on calculating the Zap amount without a Zap Event
- BugFix for video save to gallery tool
- BugFix videos of one page leaking to the next page

[Changes][v0.40.6]


<a id="v0.40.5"></a>
# [Release v0.40.5: BugFix for volume controls](https://github.com/vitorpamplona/amethyst/releases/tag/v0.40.5) - 2023-05-02



[Changes][v0.40.5]


<a id="v0.40.4"></a>
# [Release v0.40.4: MP3 Support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.40.4) - 2023-05-02

Increases visibility of buttons in Stories.
Adds basic MP3 Support (regular, chats and stories)
Revamps search filters (it should be easier to find stuff now)
Adds dimensions to NIP-94,-95 Image files.

[Changes][v0.40.4]


<a id="v0.40.3"></a>
# [Release v0.40.3:Adjustments and BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.40.3) - 2023-05-02

- BugFix for mixing NIP94 and NIP95 selections on the Video Feed
- BugFix for now showing the error message when the phone can't open the File
- Adds language to warn that NIP94 and 95 are new and other clients might not see it.
- Adds tamil translations

[Changes][v0.40.3]


<a id="v0.40.2"></a>
# [Release v0.40.2: New Video Uploading and Playback](https://github.com/vitorpamplona/amethyst/releases/tag/v0.40.2) - 2023-05-01

- Adds "Seen on Relay" information for NIP-94 and 95 posts
- Adds custom video playback controls with autoplay starting on mute
- Adds video uploading progress UI
- Adds NIP-95 in-disk caching
- Adds Zap payment confirmations without the receiver having a Zap setup
- Animates to the top after posting in the Video feed
- Keeps position in the Video feed between screen
- Fixes the Crossfade for BlurHash on NIP94 and NIP95 events

[Changes][v0.40.2]


<a id="v0.40.1"></a>
# [Release v0.40.1: BugFix for the Nostr Report](https://github.com/vitorpamplona/amethyst/releases/tag/v0.40.1) - 2023-05-01



[Changes][v0.40.1]


<a id="v0.40.0"></a>
# [Release v0.40.0: Global Video/Image Feed](https://github.com/vitorpamplona/amethyst/releases/tag/v0.40.0) - 2023-04-29



[Changes][v0.40.0]


<a id="v0.38.0"></a>
# [Release v0.38.0: Highlighter Events](https://github.com/vitorpamplona/amethyst/releases/tag/v0.38.0) - 2023-04-28

Adds support for Highlighter.com

[Changes][v0.38.0]


<a id="v0.37.4"></a>
# [Release v0.37.4: Bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.37.4) - 2023-04-27

- [Adjusts size of the Zap Forward text.](https://github.com/vitorpamplona/amethyst/commit/f173f3194bedd4e6ab418f0acb04b057553d9dc9)
- [Avoids flickering in the Global Feed](https://github.com/vitorpamplona/amethyst/commit/8ca63f32f45e6b227b2a993eb92fe1115251acb6)

[Changes][v0.37.4]


<a id="v0.37.3"></a>
# [Release v0.37.3: Trying to solve the random crash](https://github.com/vitorpamplona/amethyst/releases/tag/v0.37.3) - 2023-04-27

[Reverting compose to 1.4.2](https://github.com/vitorpamplona/amethyst/commit/1ae785061e81ec9c84c754e6d3af40a26c518fde)
[Add logs for fail to decrypt zap](https://github.com/vitorpamplona/amethyst/commit/ddba4ac5100691244f96aaac7798061580a87f55)
[Bugfix for clickable url](https://github.com/vitorpamplona/amethyst/commit/0fe6bd6c8372f58bebf238ec207479d5bb051e7b)
[Reducing the amount of filters to send to relays to 10](https://github.com/vitorpamplona/amethyst/commit/925289ae64b7921b07dca97e682f65170195c63d)

[Changes][v0.37.3]


<a id="v0.37.2"></a>
# [Release v0.37.2: non-english hashtags](https://github.com/vitorpamplona/amethyst/releases/tag/v0.37.2) - 2023-04-27

- Adds support for non-english hashtags by [@vivganes](https://github.com/vivganes)
- Improves translations of NIP-94/95 servers by [@vitorpamplona](https://github.com/vitorpamplona)
- Correctly copy-pastes NIP94 and NIP95 addresses by [@vitorpamplona](https://github.com/vitorpamplona)
- Updates dependencies by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.37.2]


<a id="v0.37.1"></a>
# [Release v0.37.1: Zap Forwarding](https://github.com/vitorpamplona/amethyst/releases/tag/v0.37.1) - 2023-04-27

- Adds Zap Address preference to the `zap` event tag
- Zaps note-tags before user info
- Enables raw URL uploads with warnings.
- Prunes NIP-95 data from memory when pausing the app.


[Changes][v0.37.1]


<a id="v0.37.0"></a>
# [Release v0.37.0: NIP-95 (Image upload to relays)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.37.0) - 2023-04-26

Adds popup to choose which image server to use by [@vitorpamplona](https://github.com/vitorpamplona)
Adds choice of NostrImg.com by [@vitorpamplona](https://github.com/vitorpamplona)
Adds choice of NIP-95 relays by [@vitorpamplona](https://github.com/vitorpamplona)
BugFix for AUTH w/ Nostr.wine by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.37.0]


<a id="v0.36.0"></a>
# [Release v0.36.0: Support for private relays  (NIP-42)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.36.0) - 2023-04-26

- Adds automatic authentication for private relays implementing NIP-42 by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.36.0]


<a id="v0.35.1"></a>
# [Release v0.35.1: Private Zaps on Polls](https://github.com/vitorpamplona/amethyst/releases/tag/v0.35.1) - 2023-04-25

- Adds support for poll votes with Private Zaps by [@vitorpamplona](https://github.com/vitorpamplona)
- Marks all Zaps to be private by default by [@vitorpamplona](https://github.com/vitorpamplona)
- Activates gif preview and translations in Zap Messages by [@vitorpamplona](https://github.com/vitorpamplona)
- Improves the performance and UI of Private Zap notifications by [@vitorpamplona](https://github.com/vitorpamplona)
- Improves the performance of FileHeader loading by [@vitorpamplona](https://github.com/vitorpamplona)
- Opens image dialog in the page of the current selection by [@vitorpamplona](https://github.com/vitorpamplona)
- Improves configuration screen for Zap types (double click and click and hold) by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds Preview for quote noting by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds New Hungarian translations by [@ZsZolee](https://github.com/ZsZolee)
BugFix for NIP-47 scheme check by [@kiwiidb](https://github.com/kiwiidb)

[Changes][v0.35.1]


<a id="v0.35.0"></a>
# [Release v0.35.0: Accessible Images/Videos](https://github.com/vitorpamplona/amethyst/releases/tag/v0.35.0) - 2023-04-25

Adds support for NIP-94 video and image descriptions for accessibility by [@vitorpamplona](https://github.com/vitorpamplona)
Adds confirmation screen before uploading an image/video by [@vitorpamplona](https://github.com/vitorpamplona)
Deprecates #[n]s tags creation by [@vitorpamplona](https://github.com/vitorpamplona)
Updates Wallet Connect API (JSON-RPC) by [@vitorpamplona](https://github.com/vitorpamplona)
Adds Private zaps notifications with messages by [@believethehype](https://github.com/believethehype)
Adds Basic Private Zap support by [@believethehype](https://github.com/believethehype)
Adds Content previews (any kind: images, videos, posts) on the new post screen by [@vitorpamplona](https://github.com/vitorpamplona)
Adds New custom hashtags for #grownostr and #footstr by [@believethehype](https://github.com/believethehype)
Adds Material You's adaptive icons by [@jltdhome](https://github.com/jltdhome)

[Changes][v0.35.0]


<a id="v0.34.1"></a>
# [Release v0.34.1: BugFixes for Polls](https://github.com/vitorpamplona/amethyst/releases/tag/v0.34.1) - 2023-04-21



[Changes][v0.34.1]


<a id="v0.34.0"></a>
# [Release v0.34.0: NIP-94 Support for Uploads](https://github.com/vitorpamplona/amethyst/releases/tag/v0.34.0) - 2023-04-21

- All uploads now create NIP-94 events.

[Changes][v0.34.0]


<a id="v0.33.2"></a>
# [Release v0.33.2: Bugfixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.33.2) - 2023-04-21

BugFix for [#359](https://github.com/vitorpamplona/amethyst/issues/359)
BugFix for [#361](https://github.com/vitorpamplona/amethyst/issues/361) by [@vivganes](https://github.com/vivganes)
Updated NL Translations by [@Bardesss](https://github.com/Bardesss)


[Changes][v0.33.2]


<a id="v0.33.1"></a>
# [Release v0.33.1: Notification dots on additive filters](https://github.com/vitorpamplona/amethyst/releases/tag/v0.33.1) - 2023-04-20



[Changes][v0.33.1]


<a id="v0.33.0"></a>
# [Release v0.33.0: Additive Filters](https://github.com/vitorpamplona/amethyst/releases/tag/v0.33.0) - 2023-04-20



[Changes][v0.33.0]


<a id="v0.32.3"></a>
# [Release v0.32.3: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.32.3) - 2023-04-07

You might see more items (quotes, invoices, reply information) being loaded on-demand, changing the view as it loads. Let me know if this isn't too annoying.

[Changes][v0.32.3]


<a id="v0.32.2"></a>
# [Release v0.32.2: Create posts with LN Invoice](https://github.com/vitorpamplona/amethyst/releases/tag/v0.32.2) - 2023-04-06

- Adds Note preview in Chat and DMs
- Adds tagging for Notes and Users in Chats
- Adds LN invoice creation in new posts
- Adds notifications for profile zaps
- Adds private replies of DMs in Notifications

[Changes][v0.32.2]


<a id="v0.32.1"></a>
# [Release v0.32.1: BugFixes for Polls](https://github.com/vitorpamplona/amethyst/releases/tag/v0.32.1) - 2023-04-04

Adds payment animation to Polls by [@vitorpamplona](https://github.com/vitorpamplona)
Fixes Voted option highlight by [@vitorpamplona](https://github.com/vitorpamplona)
Fixes Poll Color for Light mode by [@vitorpamplona](https://github.com/vitorpamplona)
Fixes margins when images are used in the poll by [@vitorpamplona](https://github.com/vitorpamplona)
Makes sure before and after voting the padding is the same by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.32.1]


<a id="v0.32.0"></a>
# [Release v0.32.0: Polls](https://github.com/vitorpamplona/amethyst/releases/tag/v0.32.0) - 2023-04-04

- Adds NIP 69 (Polls) support by [@npub1mgxvsg25hh6vazl5zl4295h6nx4xtjtvw7r86ejklnxmlrncrvvqdrffa5](https://github.com/npub1mgxvsg25hh6vazl5zl4295h6nx4xtjtvw7r86ejklnxmlrncrvvqdrffa5)
- Adds Show QR option to Profile screen by [@npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z](https://github.com/npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z)

[Changes][v0.32.0]


<a id="v0.31.4"></a>
# [Release v0.31.4: Login with QR](https://github.com/vitorpamplona/amethyst/releases/tag/v0.31.4) - 2023-04-03

- Login with QR by [@greenart7c3](https://github.com/greenart7c3)
- Adds full nip19 parsing on key decoding capabilities by [@vitorpamplona](https://github.com/vitorpamplona)
- Speeds up RichText rendering parsers by [@vitorpamplona](https://github.com/vitorpamplona)
- added message to LnZapEvent and interface by [@believethehype](https://github.com/believethehype)
- Faster processing of Contact List events by [@vitorpamplona](https://github.com/vitorpamplona)
- Faster user in follow list algo by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.31.4]


<a id="v0.31.3"></a>
# [Release v0.31.3: Adds post quotes to Notifications](https://github.com/vitorpamplona/amethyst/releases/tag/v0.31.3) - 2023-03-31



[Changes][v0.31.3]


<a id="v0.31.2"></a>
# [Release v0.31.2: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.31.2) - 2023-03-31

BugFixes:
- "author is elon" post with a regex taking over a second to parse.
- thread loading with children of the selected post not loading correctly.

[Changes][v0.31.2]


<a id="v0.31.0"></a>
# [Release v0.31.0: Wallet Connect onboarding with Alby](https://github.com/vitorpamplona/amethyst/releases/tag/v0.31.0) - 2023-03-31

- Avoids reloading information when the app resumes by [@vitorpamplona](https://github.com/vitorpamplona)
- Full Wallet Connect onboarding with Alby by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds PoW Display to the MasterNote of a Thread by [@vitorpamplona](https://github.com/vitorpamplona)
- Changes the download folder to remove Amethyst's version by [@vitorpamplona](https://github.com/vitorpamplona)
- Activates public relays for chats by [@vitorpamplona](https://github.com/vitorpamplona)
- Saves the position of the Notification feed by [@vitorpamplona](https://github.com/vitorpamplona)
- Breaks notification cards in chunks of 50 notifications each by [@vitorpamplona](https://github.com/vitorpamplona)
- Custom Zaps Hungarian Translation by [@ZsZolee](https://github.com/ZsZolee)
- Fixes thread loading issue where the clicked note's children is not loaded by [@vitorpamplona](https://github.com/vitorpamplona)
- BugFix for some events not loading by [@vitorpamplona](https://github.com/vitorpamplona)
- Updates search results as new notes come in by [@vitorpamplona](https://github.com/vitorpamplona)
- Refactoring EOSEs to consider account switching in the app by [@vitorpamplona](https://github.com/vitorpamplona)
- Centers the loading icon for image previews by [@vitorpamplona](https://github.com/vitorpamplona)
- Dependency updates by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.31.0]


<a id="v0.30.2"></a>
# [Release v0.30.2: Custom Zap amounts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.30.2) - 2023-03-29

- Adds double-click to custom Zap amounts and messages by [@Believethehype](https://github.com/Believethehype)
- Adds image upload on profile editing page by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds PoW Display to notes by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds additional Hungarian Translation by [@ZsZolee](https://github.com/ZsZolee)
- More forgiving Wallet Connect interface  by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds image url with loading icon when previews fail/take too long by [@vitorpamplona](https://github.com/vitorpamplona)
- Increases performance of the bottom navigation by [@vitorpamplona](https://github.com/vitorpamplona)
- Increases performance of relay icon rendering by [@vitorpamplona](https://github.com/vitorpamplona)
- Changes relay byte count to count all messages from relays, including errors by [@vitorpamplona](https://github.com/vitorpamplona)
- Makes the Scan QR Button available in smaller screens by [@vitorpamplona](https://github.com/vitorpamplona)
- BugFix: Long names/usernames in the Profile Page breaking the layout by [@vitorpamplona](https://github.com/vitorpamplona)
- BugFix: Stick reply setup from Gigi by [@vitorpamplona](https://github.com/vitorpamplona)
- BugFix: Sticky notifications on account change by [@vitorpamplona](https://github.com/vitorpamplona)
- BugFix: vertical position of the text in short messages with expandable content by [@vitorpamplona](https://github.com/vitorpamplona)
- BugFix for cutting links in half when they are the last word in a post by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.30.2]


<a id="v0.30.1"></a>
# [Release v0.30.1: Extra private key for Wallet Connect](https://github.com/vitorpamplona/amethyst/releases/tag/v0.30.1) - 2023-03-27

- Adds an extra secret to Zap Payments with Wallet Connect
- Adds a "Follow Back" button on user profiles by [@believethehype](https://github.com/believethehype)

[Changes][v0.30.1]


<a id="v0.30.0"></a>
# [Release v0.30.0 EOSE and Invalidate Cache refactoring](https://github.com/vitorpamplona/amethyst/releases/tag/v0.30.0) - 2023-03-26

- Improves the use of EOSE events to avoid re-downloads
- Refactors invalidate cache to allow immediate and spaced-out screen updates
- Preloads URL Previews to avoid jittering when loading
- Remembers video playback functions to avoid jittering
- Fixes feed position when looking at a note and coming back
- Updated Chinese (Traditional) Translation by [@miseelu](https://github.com/miseelu)
- Refines the look to the Zap animation
- BugFix for nostr: clickable uris
- Videos now go full screen.

[Changes][v0.30.0]


<a id="v0.29.2"></a>
# [Release v0.29.2 #SkullofSatoshi](https://github.com/vitorpamplona/amethyst/releases/tag/v0.29.2) - 2023-03-24

#SkullofSatoshi by [@believethehype](https://github.com/believethehype)

[Changes][v0.29.2]


<a id="v0.29.1"></a>
# [Release v0.29.1 `R` Tags](https://github.com/vitorpamplona/amethyst/releases/tag/v0.29.1) - 2023-03-24

Adds Support for `r` tags by [@vitorpamplona](https://github.com/vitorpamplona)
Adds support for lightning withdrawal links	by [@lgleasain](https://github.com/lgleasain)
Adds missing pt-br translations	by [@greenart7c3](https://github.com/greenart7c3)
Adds support for Translateable RichText on About Me by [@vitorpamplona](https://github.com/vitorpamplona)
Adds an image carousel to image dialog by [@vitorpamplona](https://github.com/vitorpamplona)
Adds Boosts to Profile Notes by [@vitorpamplona](https://github.com/vitorpamplona)
BugFix for nostr: scheme translations by [@vitorpamplona](https://github.com/vitorpamplona)
BugFix for unknown nevent1 post sending to profile  by [@vitorpamplona](https://github.com/vitorpamplona)
BugFix for pledged sats bolt light up by [@vitorpamplona](https://github.com/vitorpamplona)
Migrates per-relay stats from event counter to bytes transmitted by [@vitorpamplona](https://github.com/vitorpamplona)
Simplifes reply view on Notifications by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.29.1]


<a id="v0.29.0"></a>
# [Release v0.29.0: Bounties & Wallet Connect API](https://github.com/vitorpamplona/amethyst/releases/tag/v0.29.0) - 2023-03-23

- Support for Bounties by [@vitorpamplona](https://github.com/vitorpamplona)
- Support to Pledge in bounties by [@vitorpamplona](https://github.com/vitorpamplona)
- Support for video uploads by [@vitorpamplona](https://github.com/vitorpamplona)
- Support for Wallet Connect API by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds progress bars when Zapping by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds user's banner and profile picture to LongFormPosts if none is found by [@vitorpamplona](https://github.com/vitorpamplona)
- Displays all hashtags at the bottom of each event by [@vitorpamplona](https://github.com/vitorpamplona)
- Checks if the requested amount to matches the received LN Invoice's amount by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds 2 additional popular community hashtagicons by by [@Believethehype](https://github.com/Believethehype)

[Changes][v0.29.0]


<a id="v0.28.1"></a>
# [Release v0.28.1: Bug Fixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.28.1) - 2023-03-21



[Changes][v0.28.1]


<a id="v0.28.0"></a>
# [Release v0.28.0: Bookmarks](https://github.com/vitorpamplona/amethyst/releases/tag/v0.28.0) - 2023-03-20

NIP-51 Public and Private Bookmarks by [@vitorpamplona](https://github.com/vitorpamplona)
Adds Translations for Estonia by [@chiajlingvoj](https://github.com/chiajlingvoj)
Fixes URL guesser by [@vitorpamplona](https://github.com/vitorpamplona)
Fixes zap amount recalculation by [@vitorpamplona](https://github.com/vitorpamplona)
Fixes disappearing messages in chat by [@vitorpamplona](https://github.com/vitorpamplona)
Fixes tag links that did not end with a space by [@vitorpamplona](https://github.com/vitorpamplona)
User-Agent fix [@hsoc](https://github.com/hsoc)
More custom hashtags by [@Believethehype](https://github.com/Believethehype)
Update on Russian and Ukrainian translations by [@radiokot](https://github.com/radiokot)

[Changes][v0.28.0]


<a id="v0.27.2"></a>
# [Release v0.27.2: Custom hashtags](https://github.com/vitorpamplona/amethyst/releases/tag/v0.27.2) - 2023-03-17

Makes hashtags case insensitive	by [@vitorpamplona](https://github.com/vitorpamplona)
Adds special #Bitcoin hashtag icon by [@Believethehype](https://github.com/Believethehype)
Adds special #Nostr hashtag Icon by [@Believethehype](https://github.com/Believethehype)
Minor changes in Hungarian Translations by [@ZsZolee](https://github.com/ZsZolee)
Improves NIP-19 Parsing in content by [@vitorpamplona](https://github.com/vitorpamplona)
Disables online search after leaving the Search screen by [@vitorpamplona](https://github.com/vitorpamplona)
Fixes Background color of the badge's description [@vitorpamplona](https://github.com/vitorpamplona)
Fixes some missing messages on chat	by [@vitorpamplona](https://github.com/vitorpamplona)


[Changes][v0.27.2]


<a id="v0.27.1"></a>
# [Release v0.27.1: Follow Hashtags](https://github.com/vitorpamplona/amethyst/releases/tag/v0.27.1) - 2023-03-16



[Changes][v0.27.1]


<a id="v0.27.0"></a>
# [Release v0.27.0: Hashtags](https://github.com/vitorpamplona/amethyst/releases/tag/v0.27.0) - 2023-03-15

- Adds Support for Hashtags	by [@vitorpamplona](https://github.com/vitorpamplona)
- Shows LongForm Texts on Global by [@vitorpamplona](https://github.com/vitorpamplona)
- Prunes contact lists when pausing	by [@vitorpamplona](https://github.com/vitorpamplona)
- Fixes the deletion of private messages by [@vitorpamplona](https://github.com/vitorpamplona)
- Shows url host in link previews by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds the Follow verification icon to the chat header by [@vitorpamplona](https://github.com/vitorpamplona)
- Performance Improments across the app	by [@vitorpamplona](https://github.com/vitorpamplona)
- Avoids breaking urls and invoices when cutting an expandable content by [@vitorpamplona](https://github.com/vitorpamplona)
- Implements a faster Author Gallery for notifications by [@vitorpamplona](https://github.com/vitorpamplona)
- Reduces badge size in Profiles by [@vitorpamplona](https://github.com/vitorpamplona)
- Makes the Quick Action menu less starkey in dark theme by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds Block button and Dialog by [@maxmoney21m](https://github.com/maxmoney21m)
- Adds Report Dialog w/ Message by [@maxmoney21m](https://github.com/maxmoney21m)


[Changes][v0.27.0]


<a id="v0.26.2"></a>
# [Release v0.26.2: F-Droid Reproducible Build feature](https://github.com/vitorpamplona/amethyst/releases/tag/v0.26.2) - 2023-03-15



[Changes][v0.26.2]


<a id="v0.26.1"></a>
# [Release v0.26.1: BugFixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.26.1) - 2023-03-14

Fixes app crashing when a non-Hex value is found in Contact Lists
Fixes app crashing when a duplicated follow is found in Contact Lists

[Changes][v0.26.1]


<a id="v0.26.0"></a>
# [Release v0.26.0: Performance Upgrades](https://github.com/vitorpamplona/amethyst/releases/tag/v0.26.0) - 2023-03-14

Restructuring User/Note index cache to use less memory, more cpu

[Changes][v0.26.0]


<a id="v0.25.3"></a>
# [Release v0.25.3: De-Googled F-Droid Flavor](https://github.com/vitorpamplona/amethyst/releases/tag/v0.25.3) - 2023-03-14



[Changes][v0.25.3]


<a id="v0.25.2"></a>
# [Release v0.25.2: Image Upload in chats](https://github.com/vitorpamplona/amethyst/releases/tag/v0.25.2) - 2023-03-14

- Image Upload in Chat by [@believethehype](https://github.com/believethehype) and [@vitorpamplona](https://github.com/vitorpamplona)
- Bug fix for logging off crashing the app. by [@vitorpamplona](https://github.com/vitorpamplona)
- Bug fix to reset conversations tab when switching accounts. by [@maxmoney21m](https://github.com/maxmoney21m)
- Bug fix for the background color of the top bar in light mode by [@maxmoney21m](https://github.com/maxmoney21m)
- Updated Dutch translations by [@Bardesss](https://github.com/Bardesss)

[Changes][v0.25.2]


<a id="v0.25.1"></a>
# [Release v0.25.1: Performance Upgrades](https://github.com/vitorpamplona/amethyst/releases/tag/v0.25.1) - 2023-03-14

- Faster Loading times for placeholders
- Reverted DropDown menu in chats
- Bug fix for the style of the Image Upload button before permissions are granted.

[Changes][v0.25.1]


<a id="v0.25.0"></a>
# [Release v0.25.0: Multiple account management](https://github.com/vitorpamplona/amethyst/releases/tag/v0.25.0) - 2023-03-13

Adds user-agent header to OkHttpClient requests for NIP-05 Verifications by [@maxmoney21m](https://github.com/maxmoney21m)
Adds Multiple account management by [@maxmoney21m](https://github.com/maxmoney21m)
Replaces mlkit with zxing QR scanner by [@maxmoney21m](https://github.com/maxmoney21m)
Adds a way to remember feed positioning [@maxmoney21m](https://github.com/maxmoney21m)
Adds Robohash profile generation using SVGs by [@maxmoney21m](https://github.com/maxmoney21m)
Adds quick action menu in chats and threads  by [@maxmoney21m](https://github.com/maxmoney21m)
Updated Dutch translations by [@matata2140](https://github.com/matata2140) and [@Bardesss](https://github.com/Bardesss)
Corrects identity claim for mastodon by [@h3y6e](https://github.com/h3y6e)
Corrects translation files by [@akiomik](https://github.com/akiomik)
Refactorings by [@Chemaclass](https://github.com/Chemaclass) and [@kappaseijin](https://github.com/kappaseijin)

[Changes][v0.25.0]


<a id="v0.24.2"></a>
# [Release v0.24.2 NIP-39 support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.24.2) - 2023-03-10

NIP-39 Support by [@vitorpamplona](https://github.com/vitorpamplona)
Add Taiwanese translations by [@maxmoney21m](https://github.com/maxmoney21m)
BugFix: Scrollable Select screen by [@maxmoney21m](https://github.com/maxmoney21m)
Adds missing ES translations by [@Chemaclass](https://github.com/Chemaclass)
Adds missing Portuguese translations by [@greenart7c3](https://github.com/greenart7c3)
Shows private message notifications by [@vitorpamplona](https://github.com/vitorpamplona)
Upgrades encrypted storage  by [@Chemaclass](https://github.com/Chemaclass)
Moves MarkAsRead write and read to IO context by [@vitorpamplona](https://github.com/vitorpamplona)
Moves Zap amount Calculations in cache and into an IO context by [@vitorpamplona](https://github.com/vitorpamplona)
BugFix: Delays in updating UserProfile feeds by [@vitorpamplona](https://github.com/vitorpamplona)
Adds a Reporting menu option to badge notifications by [@vitorpamplona](https://github.com/vitorpamplona)
Uses full banner image sizes on User Profile pages and Drawer by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.24.2]


<a id="v0.24.1"></a>
# [Release v0.24.1: Translations and Share Post](https://github.com/vitorpamplona/amethyst/releases/tag/v0.24.1) - 2023-03-09

Adds Chinese (Traditional) Translation by [@miseelu](https://github.com/miseelu)
Adds Chinese (Simplified) Translation by [@miseelu](https://github.com/miseelu)
Adds Hungarian Translation by [@ZsZolee](https://github.com/ZsZolee)
Adds Turkish Translation by [@detherminal](https://github.com/detherminal)
Adds Share Post option with a Snort link by [@maxmoney21m](https://github.com/maxmoney21m)
Adds PubKey display in user profile by [@vitorpamplona](https://github.com/vitorpamplona)
Adds New QuickAction Menu (click and hold) by [@maxmoney21m](https://github.com/maxmoney21m)
Adds Lint enforcement and several code quality improvements by [@Chemaclass](https://github.com/Chemaclass)
Updated Russian and Ukrainian translations by [@Radiokot](https://github.com/Radiokot)
BugFix for Biometric support in older phones by [@maxmoney21m](https://github.com/maxmoney21m)
BugFix for LN invoices previews with under 350 chars by [@maxmoney21m](https://github.com/maxmoney21m)
BugFix for Badges not showing up in some profiles by [@vitorpamplona](https://github.com/vitorpamplona)
BugFix for Clickable addresses (naddr) and support for [@naddr](https://github.com/naddr) when writing a post by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.24.1]


<a id="v0.24.0"></a>
# [Release v0.24.0: Support for Badges (NIP-58)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.24.0) - 2023-03-05

Read only support for Badges (NIP-58) [@vitorpamplona](https://github.com/vitorpamplona)
Allow zooming on profile picture and banner [@maxmoney21m](https://github.com/maxmoney21m)
Refactor ui/dal Filters	[@Chemaclass](https://github.com/Chemaclass)
Fixes showing the R.string.first id instead of text [@vitorpamplona](https://github.com/vitorpamplona)
Fixes result order in the search screen	[@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.24.0]


<a id="v0.23.1"></a>
# [Release v0.23.1: Bug Fix for Image Upload crash](https://github.com/vitorpamplona/amethyst/releases/tag/v0.23.1) - 2023-03-05



[Changes][v0.23.1]


<a id="v0.23.0"></a>
# [Release v0.23.0: Search NIP-50](https://github.com/vitorpamplona/amethyst/releases/tag/v0.23.0) - 2023-03-05

- Support for NIP-50: Search by [@vitorpamplona](https://github.com/vitorpamplona), thanks to [@brugeman](https://github.com/brugeman) and [@EvgenyPlaksin](https://github.com/EvgenyPlaksin) for the first draft on this feature
- "Mark all messages as Read" feature by [@maxmoney21m](https://github.com/maxmoney21m). Let's see [@derekross](https://github.com/derekross) approves the feature and bounty
- Refactoring by [@Chemaclass](https://github.com/Chemaclass)
- Small performance improvements by [@vitorpamplona](https://github.com/vitorpamplona)
- Require biometrics to export private keys by [@maxmoney21m](https://github.com/maxmoney21m)
- Support for relative paths on preview card's `OG:IMAGE` tags by [@vitorpamplona](https://github.com/vitorpamplona)
- Changes the navigation bar's magnifying glass icon to globe by [@maxmoney21m](https://github.com/maxmoney21m)
- French translation by [@bkBrunobk](https://github.com/bkBrunobk)
- Add Password Managers autofill support to the login screen by [@Radiokot](https://github.com/Radiokot)

[Changes][v0.23.0]


<a id="v0.22.3"></a>
# [Release v0.22.3: BugFix for Snort's relay](https://github.com/vitorpamplona/amethyst/releases/tag/v0.22.3) - 2023-03-04

- Performance improvements by [@vitorpamplona](https://github.com/vitorpamplona)
- NIP19 Refactoring by [@Chemaclass](https://github.com/Chemaclass)
- Delete Origin Header as a bug fix of the bugfix for Snort's relay by [@vitorpamplona](https://github.com/vitorpamplona)
- RTL Improvements by [@rashedswen](https://github.com/rashedswen)

[Changes][v0.22.3]


<a id="v0.22.2"></a>
# [Release v0.22.2: Backup Keys](https://github.com/vitorpamplona/amethyst/releases/tag/v0.22.2) - 2023-03-03

- Moves nsec backup to Drawer and Dialog, organize Drawer by [@maxmoney21m](https://github.com/maxmoney21m)
- Migrates underlying architecture to avoid using memory-inefficient ByteArrays to store Nostr Events by [@vitorpamplona](https://github.com/vitorpamplona)
- Adds Spanish translations by [@Chemaclass](https://github.com/Chemaclass) and [@JesusValera](https://github.com/JesusValera)
- Displays Group Info in the timeline for quotes of the Creation Event. by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.22.2]


<a id="v0.22.1"></a>
# [Release v0.22.1: Replaceable Events NIP-33](https://github.com/vitorpamplona/amethyst/releases/tag/v0.22.1) - 2023-03-03

- Adds a Follow Action in a Note's Menu by [@rashedswen](https://github.com/rashedswen)
- Support for Replaceable Events (NIP-33) by [@vitorpamplona](https://github.com/vitorpamplona)
- Add Ukrainian (uk) translation [@Radiokot](https://github.com/Radiokot)
- Add Russian (ru) translation by [@Radiokot](https://github.com/Radiokot)
- Fixes Preview Error when the url is invalid by [@vitorpamplona](https://github.com/vitorpamplona)
- Brazilian translations correction by [@marioaugustorama](https://github.com/marioaugustorama)

[Changes][v0.22.1]


<a id="v0.22.0"></a>
# [Release v0.22.0: Markdown & Long Content (NIP-23)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.22.0) - 2023-03-02

Markdown Support by [@vitorpamplona](https://github.com/vitorpamplona)
Long-form Content (NIP-23) Support by [@vitorpamplona](https://github.com/vitorpamplona)

[Changes][v0.22.0]


<a id="v0.21.2"></a>
# [Release v0.21.2: BugFix for Arabic](https://github.com/vitorpamplona/amethyst/releases/tag/v0.21.2) - 2023-03-01

- Hides hidden users from main feeds.
- Lowers the speed of the updates of the feed.
- Fix for a String.format on robohash crashing the app in Arabic

[Changes][v0.21.2]


<a id="v0.21.1"></a>
# [Release v0.21.1](https://github.com/vitorpamplona/amethyst/releases/tag/v0.21.1) - 2023-03-01

Amethyst v0.21.1: Peace

- Reply Notifications only show when:
  1. Reply directly cites the user in the reply note
  2. Reply to an event authored by the user
- Internationalization
  1. Arabic translations [@rashedswen](https://github.com/rashedswen)
  2. Portuguese-Brazil translations by [@greenart7c3](https://github.com/greenart7c3)
- Fixes the size of all pictures using the proxy to 200
- Adds an Origin header to connect with Snort's relay.



[Changes][v0.21.1]


<a id="v0.20.5"></a>
# [Release v0.20.5: PlayStore Re-submit](https://github.com/vitorpamplona/amethyst/releases/tag/v0.20.5) - 2023-03-01



[Changes][v0.20.5]


<a id="v0.20.4"></a>
# [Release v0.20.4: Updating Reports to the latest spec](https://github.com/vitorpamplona/amethyst/releases/tag/v0.20.4) - 2023-03-01



[Changes][v0.20.4]


<a id="v0.20.3"></a>
# [Release v0.20.3: Merge Notification Types](https://github.com/vitorpamplona/amethyst/releases/tag/v0.20.3) - 2023-02-28



[Changes][v0.20.3]


<a id="v0.20.2"></a>
# [Release v0.20.2: Conversations++](https://github.com/vitorpamplona/amethyst/releases/tag/v0.20.2) - 2023-02-27

Filtering Notifications to the events that cite the user directly (experimental).
Showing direct reply as a Quote.
Marks as read regardless of note.event status.
ThreadView avoids double loading.
Clearly marks the clicked item on ThreadView
Allows users to delete usernames
Adds nip05 to Chatroom headers.
Adds time of the boost and menu options for the boosted event.

[Changes][v0.20.2]


<a id="v0.20.1"></a>
# [Release v0.20.1: BugFix for invalid NIP05 addresses crashing the app](https://github.com/vitorpamplona/amethyst/releases/tag/v0.20.1) - 2023-02-27

1. BugFix for invalid nip05 domain names crashing the app.
2. Removing _ from the display
3. Checks if NIP 05 is an email before starting to verify to avoid crashes.
4. BugFix for long NIP05 taking multiple lines.
5. Adds NIP05 to the main note in a thread

[Changes][v0.20.1]


<a id="v0.20.0"></a>
# [Release v0.20.0: Nip05 Support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.20.0) - 2023-02-27



[Changes][v0.20.0]


<a id="v0.19.1"></a>
# [Release v0.19.1: Crops reply information to 2](https://github.com/vitorpamplona/amethyst/releases/tag/v0.19.1) - 2023-02-26

1. Crops Reply Information to 2, follows first, expandable to see others
2. Hides bottom navigation bar when keyboard is visible.

[Changes][v0.19.1]


<a id="v0.19.0"></a>
# [Release v0.19.0: NIP-09 Event Deletion support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.19.0) - 2023-02-26

Event Deletion works with TextNotes, Likes, Boosts, and Reports.

[Changes][v0.19.0]


<a id="v0.18.3"></a>
# [Release v0.18.3: Displaying Error messages when Zaps fail](https://github.com/vitorpamplona/amethyst/releases/tag/v0.18.3) - 2023-02-26

- Displaying Error messages when Zaps fail
- Animating the appearance of the reply note when replying in chats.


[Changes][v0.18.3]


<a id="v0.18.2"></a>
# [Release v0.18.2: Multiple Zaps per post](https://github.com/vitorpamplona/amethyst/releases/tag/v0.18.2) - 2023-02-25

- Improvements to ShowMore background gradient and size of bubbles in chat.
- Fixes Ugly Tab Background color in the light theme
- Removes the block on Multiple Zaps in a single post (copy paste from the like button, that has a block to avoid multiple likes)
- Fixes liveSet to delete. | Solves the outofsync error chat has been reporting.
- Synchronizes `Client.connect` to avoid random number in relay status
- Fixes the URL detector for account for 2 special characters in Chinese  "，" and "。"
- Reverts fixing the English language to avoid issues with translation
- Updating dependencies


[Changes][v0.18.2]


<a id="v0.18.1"></a>
# [Release v0.18.1: Know your spammers](https://github.com/vitorpamplona/amethyst/releases/tag/v0.18.1) - 2023-02-24

- New Relay Info UI displays the number of messages that were duplicated and deleted per relay.
- Improvements to Home feed update speed (75ms -> 10ms)
- Correctly deletes a slave note
- Updates observers when deleting notes
- Improved timing log
- Home doesn't need isAcceptable filter since all posts are coming from follows. Much faster.
- BugFix Loading one thread after another crashed the app
- Logs Spam count per relay.
- Removing PlayServices tag to import qrcode model
- Using language identification model bundled into the app
- Moving barcode scanning model to ship with the app.

[Changes][v0.18.1]


<a id="v0.18.0"></a>
# [Release v0.18.0: Zaps, Replies and Likes in Public Chats and Private Messages](https://github.com/vitorpamplona/amethyst/releases/tag/v0.18.0) - 2023-02-24

Zaps in Private Messages are still public, though.

- Zaps, Likes, and Replies in Public Chats and Private Messages
- Fixes bug when the note is not included in the returning set for ThreadView
- Fixing [#149](https://github.com/vitorpamplona/amethyst/issues/149)

[Changes][v0.18.0]


<a id="v0.17.11"></a>
# [Release v0.17.11: Language Preferences](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.11) - 2023-02-24

Save translation preferences between each language pair
Displays App version in the drawer
Makes the Drawer's Name/Follower count clickable
Fixes Zap amount rounding error.

[Changes][v0.17.11]


<a id="v0.17.10"></a>
# [Release v0.17.10: New Zap Amount Customization screen](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.10) - 2023-02-24

New Zap Amount Customization screen
ThreadView: Avoids changing the position of the list when the list updates.
Marking the selected post in the thread view
App Language fixed to English for now ([@Radiokot](https://github.com/Radiokot))
Properly removes a note from the database on memory trimming
Uses a Name to make the translation layer skip ln addresses
Fixes Channel message deletion bug.

[Changes][v0.17.10]


<a id="v0.17.9"></a>
# [Release v0.17.9: Blocks users from Reporting themselves](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.9) - 2023-02-22

Blocks users from Reporting themselves
Fixes missing Relay Information in Profile Pages
Adds LnZapRequest to the event observer as well to download authors.
Puts Follow and Unfollow Buttons into coroutines.
Makes sure people are only added to the hidden list if 5 or more different Nostr events have the same message.
Fixing remaining Follow/Unfollow issue with an outdated Account object.

[Changes][v0.17.9]


<a id="v0.17.8"></a>
# [Release v0.17.8: BugFix Translations breaking URLs/Images/Tags](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.8) - 2023-02-22



[Changes][v0.17.8]


<a id="v0.17.7"></a>
# [Release v0.17.7: BugFix for Invalid Hexes in Tags](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.7) - 2023-02-22



[Changes][v0.17.7]


<a id="v0.17.6"></a>
# [Release v0.17.6: BugFix for Alternating Follow/Unfollow](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.6) - 2023-02-22



[Changes][v0.17.6]


<a id="v0.17.5"></a>
# [Release v0.17.5: 0 Follows no more.](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.5) - 2023-02-21

- Fixes issue with overriding follow list to zero (maintains a local copy. It might still override to a previous version)
- Fixes Notification Dot for Zaps
- Faster notification updates
- Refactoring the saving of user preferences
- More caching to increase speed of updating lists
- Potentially fixing Notifications crash on Samsungs

[Changes][v0.17.5]


<a id="v0.17.4"></a>
# [Release v0.17.4: 5 Strike anti-spam](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.4) - 2023-02-21

- Anti-spam filter requires 5 duplicated msgs to temporarily block
- Forces conversion to npub in notes to make sure the Hex key is correct.

[Changes][v0.17.4]


<a id="v0.17.3"></a>
# [Release v0.17.3: Check Hex keys in Event Tags](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.3) - 2023-02-21



[Changes][v0.17.3]


<a id="v0.17.2"></a>
# [Release v0.17.2: Quote Note Popup and Rendering](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.2) - 2023-02-20

https://nostr.build/i/nostr.build_0cd188713619b44b8f9c214617130db7f11c427dcdc03872fac736d74a594b5a.png

- Popup to Boost/Quote Notes
- Rendering Notes instead of just presenting the Note ID
- Fixes Movement of the post when Show More is pressed
- Notification Background is now opens note
- Changes keyboard layout to Decimal in Zap amount Setup
- Fixes erasing edited Zap amounts on recomposing
- Resets mentions after writing a reply utxo
- Fixes the "Show Anyway" button in the first element of the threadview.

[Changes][v0.17.2]


<a id="v0.17.1"></a>
# [Release v0.17.1: Custom Zap Amounts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.1) - 2023-02-20



[Changes][v0.17.1]


<a id="v0.17.0"></a>
# [Release v0.17.0: Anti-spam de-dup filters](https://github.com/vitorpamplona/amethyst/releases/tag/v0.17.0) - 2023-02-19

v0.17.0: Anti-spam filters
- Filters out the duplicated-message type of Spam
- Fixes Reply not showing up in the Thread View after user Posts a new one
- Adds Reports Tab to Profile Page
- Significantly improves the speed of the Follower calculation
- Decreased User/Note object size -> 4x more objects can fit in memory.
- Prunes memory to 1000 messages per public channel
- Improves the speed of rendering long threads and thread level calculation
- Fixes Quoted Post appearing as a Reply
- Fixes Mutex state when Android kills threads.
- Refactors current filter classes into Relay filters and Screen filters
- Moves Notification Dot calculation to background threads.
- Loads new Posts on the Thread view on the fly

[Changes][v0.17.0]


<a id="v0.16.2"></a>
# [Release v0.16.2](https://github.com/vitorpamplona/amethyst/releases/tag/v0.16.2) - 2023-02-17



[Changes][v0.16.2]


<a id="v0.16.1"></a>
# [Release v0.16.1: BugFix Release](https://github.com/vitorpamplona/amethyst/releases/tag/v0.16.1) - 2023-02-17

1. Fixes the Relay List going back to defaults if the user hasn't setup their relay sets.
2. Fixes crashes from ChannelCreateEvent and ChannelMetadataEvent when the content is not a JSON structure.
3. Removes duplicated Report filters to relays.

[Changes][v0.16.1]


<a id="v0.16.0"></a>
# [Release v0.16.0: Image Proxy Resizer + CDN](https://github.com/vitorpamplona/amethyst/releases/tag/v0.16.0) - 2023-02-15



[Changes][v0.16.0]


<a id="v0.15.9"></a>
# [Release v0.15.9: Profile Delay Fix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.15.9) - 2023-02-15



[Changes][v0.15.9]


<a id="v0.15.8"></a>
# [Release v0.15.8: Follower Count BugFix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.15.8) - 2023-02-15



[Changes][v0.15.8]


<a id="v0.15.7"></a>
# [Release v0.15.7: Performance Adjustments](https://github.com/vitorpamplona/amethyst/releases/tag/v0.15.7) - 2023-02-14



[Changes][v0.15.7]


<a id="v0.15.6"></a>
# [Release v0.15.6: Performance Adjustments](https://github.com/vitorpamplona/amethyst/releases/tag/v0.15.6) - 2023-02-14



[Changes][v0.15.6]


<a id="v0.15.5"></a>
# [Release v0.15.5: Performance Adjustments](https://github.com/vitorpamplona/amethyst/releases/tag/v0.15.5) - 2023-02-14



[Changes][v0.15.5]


<a id="v0.15.4"></a>
# [Release v0.15.4: Performance Adjustments](https://github.com/vitorpamplona/amethyst/releases/tag/v0.15.4) - 2023-02-14



[Changes][v0.15.4]


<a id="v0.15.3"></a>
# [Release v0.15.3: NewPost adjusts to keyboard in long posts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.15.3) - 2023-02-13



[Changes][v0.15.3]


<a id="v0.15.2"></a>
# [Release v0.15.2: Image Uploading Feedback](https://github.com/vitorpamplona/amethyst/releases/tag/v0.15.2) - 2023-02-13

Image Uploading Feedback by [@Radiokot](https://github.com/Radiokot)
Performance Updates
Quick Fix for keeping the Edit Text with the available screen size in case of long posts.

[Changes][v0.15.2]


<a id="v0.15.1"></a>
# [Release v0.15.1: Zaps](https://github.com/vitorpamplona/amethyst/releases/tag/v0.15.1) - 2023-02-13



[Changes][v0.15.1]


<a id="v0.14.3"></a>
# [Release v0.14.3: Deactivates Proguard](https://github.com/vitorpamplona/amethyst/releases/tag/v0.14.3) - 2023-02-10



[Changes][v0.14.3]


<a id="v0.14.2"></a>
# [Release v0.14.2: Activates Proguard / Fixes Image Upload](https://github.com/vitorpamplona/amethyst/releases/tag/v0.14.2) - 2023-02-10

1. Fixes Image Upload Permissions for some phones
2. Allows unsecured (`ws://`) relay additions for Umbrel users.
3. Allows the user to leave and join their own groups
4. Fixes bad URI on Users Profile crashing the app
5. Fixes QR Code display and Scan for smaller screens.
6. Fixes a translation not showing up in old phones (dependency issue)
7. Activates ProGuard to minimize code automatically (random bugs expected)
8. Improved Relay-based user search performance.

[Changes][v0.14.2]


<a id="v0.14.1"></a>
# [Release v0.14.1: Save Images / LNURL edit](https://github.com/vitorpamplona/amethyst/releases/tag/v0.14.1) - 2023-02-09

- Save image by [Oleg Koretsky](https://github.com/Radiokot)
- LNURL edit in the profile
- Bugfix crashing the Lightning Tips with nonnumeric values in the amount.

[Changes][v0.14.1]


<a id="v0.14.0"></a>
# [Release v0.14.0: Lightning Tips](https://github.com/vitorpamplona/amethyst/releases/tag/v0.14.0) - 2023-02-08



[Changes][v0.14.0]


<a id="v0.13.3"></a>
# [Release v0.13.3: Auto-translation options](https://github.com/vitorpamplona/amethyst/releases/tag/v0.13.3) - 2023-02-08



[Changes][v0.13.3]


<a id="v0.13.2"></a>
# [Release v0.13.2: Tracks and Displays Relay Info per Event](https://github.com/vitorpamplona/amethyst/releases/tag/v0.13.2) - 2023-02-07

Amethyst v0.13.2: Tracks and Displays Relay Info per Event

1. Users can now see which relays are sending posts, DMs, and public chat messages. This uses the relay's website favicon. Please update your favicons to something relevant to users :)

2. Changes the default relay set to Paid relays for Chats and Global, making spam less impactful for new users.

3. Timeline posts are cropped at 350 chars with Show More to expand

4. Full Profile Pages by Oleg Koretsky

5. Search improvements by Habib Okanla

6. Devs can now have 2 Amethysts running by greenart7c3

7. Removes the trailing / on relay's setup by middlingphys

[Changes][v0.13.2]


<a id="v0.13.1"></a>
# [Release v0.13.1: Relay Sets (home, dm, chat, global)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.13.1) - 2023-02-06



[Changes][v0.13.1]


<a id="v0.13.0"></a>
# [Release v0.13.0: Auto-translate](https://github.com/vitorpamplona/amethyst/releases/tag/v0.13.0) - 2023-02-05



[Changes][v0.13.0]


<a id="v0.12.1"></a>
# [Release v0.12.1: More Performance!](https://github.com/vitorpamplona/amethyst/releases/tag/v0.12.1) - 2023-02-03



[Changes][v0.12.1]


<a id="v0.12.0"></a>
# [Release v0.12.0: Performance Upgrades](https://github.com/vitorpamplona/amethyst/releases/tag/v0.12.0) - 2023-02-03



[Changes][v0.12.0]


<a id="v0.11.7"></a>
# [Release v0.11.7: Relay List View](https://github.com/vitorpamplona/amethyst/releases/tag/v0.11.7) - 2023-01-31

Relay list view in profile, clickable nostr: elements, and several small improvements across the board.

[Changes][v0.11.7]


<a id="v0.11.6"></a>
# [Release v0.11.6: Bugfix: Invalid URIs in the Relay list crashing the app](https://github.com/vitorpamplona/amethyst/releases/tag/v0.11.6) - 2023-01-30



[Changes][v0.11.6]


<a id="v0.11.5"></a>
# [Release v0.11.5: NIP 56 Support](https://github.com/vitorpamplona/amethyst/releases/tag/v0.11.5) - 2023-01-30



[Changes][v0.11.5]


<a id="v0.11.4"></a>
# [Release v0.11.4: User QR Code Display & Scan (any NIP21 works)](https://github.com/vitorpamplona/amethyst/releases/tag/v0.11.4) - 2023-01-29



[Changes][v0.11.4]


<a id="v0.11.3"></a>
# [Release v0.11.3: Hide User on Profile page](https://github.com/vitorpamplona/amethyst/releases/tag/v0.11.3) - 2023-01-28



[Changes][v0.11.3]


<a id="v0.11.2"></a>
# [Release v0.11.2: Search includes Public Chats and Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.11.2) - 2023-01-28



[Changes][v0.11.2]


<a id="v0.11.1"></a>
# [Release v0.11.1: Accept Terms Login Checkbox](https://github.com/vitorpamplona/amethyst/releases/tag/v0.11.1) - 2023-01-27



[Changes][v0.11.1]


<a id="v0.11.0"></a>
# [Release v0.11.0: Notification Bubbles and Verified Follows](https://github.com/vitorpamplona/amethyst/releases/tag/v0.11.0) - 2023-01-27



[Changes][v0.11.0]


<a id="v0.10.7"></a>
# [Release v0.10.7](https://github.com/vitorpamplona/amethyst/releases/tag/v0.10.7) - 2023-01-26



[Changes][v0.10.7]


<a id="v0.10.6"></a>
# [Release v0.10.6: Relay List BugFix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.10.6) - 2023-01-26

Fixes:
- Multiple like events
- Relay List loading issue
- Case Sensitive Search box
- Proper RTL ([@KotlinGeekDev](https://github.com/KotlinGeekDev))
- Proper Navigation in the Thread Views ([@KotlinGeekDev](https://github.com/KotlinGeekDev))

[Changes][v0.10.6]


<a id="v0.10.5"></a>
# [Release v0.10.5: BugFix Release](https://github.com/vitorpamplona/amethyst/releases/tag/v0.10.5) - 2023-01-25



[Changes][v0.10.5]


<a id="v0.10.4"></a>
# [Release v0.10.4: NIP-21 and Split home feeds](https://github.com/vitorpamplona/amethyst/releases/tag/v0.10.4) - 2023-01-25

Support for [NIP-21](https://github.com/nostr-protocol/nips/blob/urlscheme/21.md), split home screen feed between Follows and Follows + Replies and some improvements in Chatroom navigation and Network use.

[Changes][v0.10.4]


<a id="v0.10.3"></a>
# [Release v0.10.3: Block Users, Report Posts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.10.3) - 2023-01-24



[Changes][v0.10.3]


<a id="v0.10.2"></a>
# [Release v0.10.2: BugFix for certain RelayList payloads](https://github.com/vitorpamplona/amethyst/releases/tag/v0.10.2) - 2023-01-24



[Changes][v0.10.2]


<a id="v0.10.1"></a>
# [Release v0.10.1: Version Code bugfix](https://github.com/vitorpamplona/amethyst/releases/tag/v0.10.1) - 2023-01-23



[Changes][v0.10.1]


<a id="v0.10.0"></a>
# [Release v0.10.0: Relay List View/Edit](https://github.com/vitorpamplona/amethyst/releases/tag/v0.10.0) - 2023-01-23



[Changes][v0.10.0]


<a id="v0.9.6"></a>
# [Release v0.9.6: Unique View Counts](https://github.com/vitorpamplona/amethyst/releases/tag/v0.9.6) - 2023-01-22



[Changes][v0.9.6]


<a id="v0.9.5"></a>
# [Release v0.9.5: Spam Filter for Private Messages](https://github.com/vitorpamplona/amethyst/releases/tag/v0.9.5) - 2023-01-21

- Spam Filter for Private Messages
- Improvements in Image Caching
- Improvements in Navigation

[Changes][v0.9.5]


<a id="v0.9.4"></a>
# [Release v0.9.4: Bug Fix Release](https://github.com/vitorpamplona/amethyst/releases/tag/v0.9.4) - 2023-01-21

Fixes Image Profile Loading

[Changes][v0.9.4]


<a id="v0.9.3"></a>
# [Release v0.9.3: Profile Edits](https://github.com/vitorpamplona/amethyst/releases/tag/v0.9.3) - 2023-01-21

- Profile Edit
- Navigation Improvements (Back Button) by https://github.com/KotlinGeekDev
- ~75% smaller apk size by https://github.com/KotlinGeekDev

[Changes][v0.9.3]


<a id="v0.9.2"></a>
# [Release v0.9.2: Bug Fix Release](https://github.com/vitorpamplona/amethyst/releases/tag/v0.9.2) - 2023-01-20

fixes a user tagging bug and a like count bug (turns out relays don't do hex id crop in tag search)

[Changes][v0.9.2]


<a id="v0.9.1"></a>
# [Release v0.9.1: Quote Reply in Chat + small fixes](https://github.com/vitorpamplona/amethyst/releases/tag/v0.9.1) - 2023-01-19



[Changes][v0.9.1]


<a id="v0.9"></a>
# [Release v0.9: Public Chat Management](https://github.com/vitorpamplona/amethyst/releases/tag/v0.9) - 2023-01-19

Demo: https://nostr.build/i/6112.gif


[Changes][v0.9]


<a id="v0.8.2"></a>
# [Release v0.8.2: Performance Improvements and Searchbar](https://github.com/vitorpamplona/amethyst/releases/tag/v0.8.2) - 2023-01-18



[Changes][v0.8.2]


<a id="v0.8.1"></a>
# [Release v0.8.1: Performance Improvements](https://github.com/vitorpamplona/amethyst/releases/tag/v0.8.1) - 2023-01-18



[Changes][v0.8.1]


<a id="v0.8"></a>
# [Release v0.8: User Tags / New Logo](https://github.com/vitorpamplona/amethyst/releases/tag/v0.8) - 2023-01-18

- New Logo
- User/Search tagging on composing
- Thread safe improvements


[Changes][v0.8]


<a id="v0.7"></a>
# [Release v0.7: Public Chats](https://github.com/vitorpamplona/amethyst/releases/tag/v0.7) - 2023-01-16

Support for:
- Anigma's Public Chat (NIP-28)
- Gifs and SVG Images.
- User Tags on Posts.


[Changes][v0.7]


<a id="v0.6"></a>
# [Release v0.6: User Profiles](https://github.com/vitorpamplona/amethyst/releases/tag/v0.6) - 2023-01-16

Demo: https://i.imgur.com/Kdvl78r.jpg

Support for:
- User Profiles
- Follow/Unfollow
- Start Message
- List of Followers and Follows
- User banner

[Changes][v0.6]


<a id="v0.5"></a>
# [Release v0.5: Private Messages](https://github.com/vitorpamplona/amethyst/releases/tag/v0.5) - 2023-01-14

Demo: https://i.imgur.com/f5vptAv.mp4

Amethyst 0.5:
- Adds support for Private Messages
- Removes autoplay from videos (have to figure out how to mute first)
- Click on Image to Zoom
- Blocks double likes

[Changes][v0.5]


<a id="v0.4"></a>
# [Release v0.4: LnInvoices](https://github.com/vitorpamplona/amethyst/releases/tag/v0.4) - 2023-01-13

- Lightning Invoice cards
- Url Preview Caching
- Long press to copy Text/NoteID/UserID

[Changes][v0.4]


<a id="v0.3"></a>
# [Release v0.3: Video/Image/Url Previews](https://github.com/vitorpamplona/amethyst/releases/tag/v0.3) - 2023-01-13

- Video playback in the timeline
- Direct image upload
- Image and URL previews when writing new posts

[Changes][v0.3]


<a id="v0.2"></a>
# [Release v0.2: Thread View](https://github.com/vitorpamplona/amethyst/releases/tag/v0.2) - 2023-01-12

- Post and Thread Views

[Changes][v0.2]


<a id="v0.1"></a>
# [Release v0.1: Basic Social App](https://github.com/vitorpamplona/amethyst/releases/tag/v0.1) - 2023-01-11

First public version with:
- Account Management
- Home Feed
- Notifications Feed
- Global Feed
- Reactions (like, boost, reply)
- Image Preview
-  Url Preview

[Changes][v0.1]


[v1.03.0]: https://github.com/vitorpamplona/amethyst/compare/v1.02.1...v1.03.0
[v1.02.1]: https://github.com/vitorpamplona/amethyst/compare/v1.02.0...v1.02.1
[v1.02.0]: https://github.com/vitorpamplona/amethyst/compare/v1.01.0...v1.02.0
[v1.01.0]: https://github.com/vitorpamplona/amethyst/compare/v1.00.5...v1.01.0
[v1.00.5]: https://github.com/vitorpamplona/amethyst/compare/v1.00.4...v1.00.5
[v1.00.4]: https://github.com/vitorpamplona/amethyst/compare/v1.00.3...v1.00.4
[v1.00.3]: https://github.com/vitorpamplona/amethyst/compare/v1.00.2...v1.00.3
[v1.00.2]: https://github.com/vitorpamplona/amethyst/compare/v1.00.1...v1.00.2
[v1.00.1]: https://github.com/vitorpamplona/amethyst/compare/v1.00.0...v1.00.1
[v1.00.0]: https://github.com/vitorpamplona/amethyst/compare/v0.94.3...v1.00.0
[v0.94.3]: https://github.com/vitorpamplona/amethyst/compare/v0.94.2...v0.94.3
[v0.94.2]: https://github.com/vitorpamplona/amethyst/compare/v0.94.1...v0.94.2
[v0.94.1]: https://github.com/vitorpamplona/amethyst/compare/v0.94.0...v0.94.1
[v0.94.0]: https://github.com/vitorpamplona/amethyst/compare/v0.93.1...v0.94.0
[v0.93.1]: https://github.com/vitorpamplona/amethyst/compare/v0.93.0...v0.93.1
[v0.93.0]: https://github.com/vitorpamplona/amethyst/compare/v0.92.7...v0.93.0
[v0.92.7]: https://github.com/vitorpamplona/amethyst/compare/v0.92.6...v0.92.7
[v0.92.6]: https://github.com/vitorpamplona/amethyst/compare/v0.92.5...v0.92.6
[v0.92.5]: https://github.com/vitorpamplona/amethyst/compare/v0.92.4...v0.92.5
[v0.92.4]: https://github.com/vitorpamplona/amethyst/compare/v0.92.3...v0.92.4
[v0.92.3]: https://github.com/vitorpamplona/amethyst/compare/v0.92.2...v0.92.3
[v0.92.2]: https://github.com/vitorpamplona/amethyst/compare/v0.92.1...v0.92.2
[v0.92.1]: https://github.com/vitorpamplona/amethyst/compare/v0.92.0...v0.92.1
[v0.92.0]: https://github.com/vitorpamplona/amethyst/compare/v0.91.0...v0.92.0
[v0.91.0]: https://github.com/vitorpamplona/amethyst/compare/v0.90.6...v0.91.0
[v0.90.6]: https://github.com/vitorpamplona/amethyst/compare/v0.90.5...v0.90.6
[v0.90.5]: https://github.com/vitorpamplona/amethyst/compare/v0.90.4...v0.90.5
[v0.90.4]: https://github.com/vitorpamplona/amethyst/compare/v0.90.3...v0.90.4
[v0.90.3]: https://github.com/vitorpamplona/amethyst/compare/v0.90.2...v0.90.3
[v0.90.2]: https://github.com/vitorpamplona/amethyst/compare/v0.90.1...v0.90.2
[v0.90.1]: https://github.com/vitorpamplona/amethyst/compare/v0.90.0...v0.90.1
[v0.90.0]: https://github.com/vitorpamplona/amethyst/compare/v0.89.10...v0.90.0
[v0.89.10]: https://github.com/vitorpamplona/amethyst/compare/v0.89.9...v0.89.10
[v0.89.9]: https://github.com/vitorpamplona/amethyst/compare/v0.89.8...v0.89.9
[v0.89.8]: https://github.com/vitorpamplona/amethyst/compare/v0.89.7...v0.89.8
[v0.89.7]: https://github.com/vitorpamplona/amethyst/compare/v0.89.6...v0.89.7
[v0.89.6]: https://github.com/vitorpamplona/amethyst/compare/v0.89.5...v0.89.6
[v0.89.5]: https://github.com/vitorpamplona/amethyst/compare/v0.89.4...v0.89.5
[v0.89.4]: https://github.com/vitorpamplona/amethyst/compare/v0.89.2...v0.89.4
[v0.89.2]: https://github.com/vitorpamplona/amethyst/compare/v0.89.1...v0.89.2
[v0.89.1]: https://github.com/vitorpamplona/amethyst/compare/v0.89.0...v0.89.1
[v0.89.0]: https://github.com/vitorpamplona/amethyst/compare/v0.88.7...v0.89.0
[v0.88.7]: https://github.com/vitorpamplona/amethyst/compare/v0.88.6...v0.88.7
[v0.88.6]: https://github.com/vitorpamplona/amethyst/compare/v0.88.5...v0.88.6
[v0.88.5]: https://github.com/vitorpamplona/amethyst/compare/v0.88.4...v0.88.5
[v0.88.4]: https://github.com/vitorpamplona/amethyst/compare/v0.88.3...v0.88.4
[v0.88.3]: https://github.com/vitorpamplona/amethyst/compare/v0.88.2...v0.88.3
[v0.88.2]: https://github.com/vitorpamplona/amethyst/compare/v0.88.1...v0.88.2
[v0.88.1]: https://github.com/vitorpamplona/amethyst/compare/v0.88.0...v0.88.1
[v0.88.0]: https://github.com/vitorpamplona/amethyst/compare/v0.87.7...v0.88.0
[v0.87.7]: https://github.com/vitorpamplona/amethyst/compare/v0.87.6...v0.87.7
[v0.87.6]: https://github.com/vitorpamplona/amethyst/compare/v0.87.5...v0.87.6
[v0.87.5]: https://github.com/vitorpamplona/amethyst/compare/v0.87.4...v0.87.5
[v0.87.4]: https://github.com/vitorpamplona/amethyst/compare/v0.87.3...v0.87.4
[v0.87.3]: https://github.com/vitorpamplona/amethyst/compare/v0.87.2...v0.87.3
[v0.87.2]: https://github.com/vitorpamplona/amethyst/compare/v0.87.1...v0.87.2
[v0.87.1]: https://github.com/vitorpamplona/amethyst/compare/v0.87.0...v0.87.1
[v0.87.0]: https://github.com/vitorpamplona/amethyst/compare/v0.86.5...v0.87.0
[v0.86.5]: https://github.com/vitorpamplona/amethyst/compare/v0.86.4...v0.86.5
[v0.86.4]: https://github.com/vitorpamplona/amethyst/compare/v0.86.3...v0.86.4
[v0.86.3]: https://github.com/vitorpamplona/amethyst/compare/v0.86.2...v0.86.3
[v0.86.2]: https://github.com/vitorpamplona/amethyst/compare/v0.86.1...v0.86.2
[v0.86.1]: https://github.com/vitorpamplona/amethyst/compare/v0.86.0...v0.86.1
[v0.86.0]: https://github.com/vitorpamplona/amethyst/compare/v0.85.3...v0.86.0
[v0.85.3]: https://github.com/vitorpamplona/amethyst/compare/v0.85.1...v0.85.3
[v0.85.1]: https://github.com/vitorpamplona/amethyst/compare/v0.85.0...v0.85.1
[v0.85.0]: https://github.com/vitorpamplona/amethyst/compare/v0.84.3...v0.85.0
[v0.84.3]: https://github.com/vitorpamplona/amethyst/compare/v0.84.2...v0.84.3
[v0.84.2]: https://github.com/vitorpamplona/amethyst/compare/v0.84.1...v0.84.2
[v0.84.1]: https://github.com/vitorpamplona/amethyst/compare/v0.83.13...v0.84.1
[v0.83.13]: https://github.com/vitorpamplona/amethyst/compare/v0.83.12...v0.83.13
[v0.83.12]: https://github.com/vitorpamplona/amethyst/compare/v0.83.10...v0.83.12
[v0.83.10]: https://github.com/vitorpamplona/amethyst/compare/v0.83.9...v0.83.10
[v0.83.9]: https://github.com/vitorpamplona/amethyst/compare/v0.83.8...v0.83.9
[v0.83.8]: https://github.com/vitorpamplona/amethyst/compare/v0.83.7...v0.83.8
[v0.83.7]: https://github.com/vitorpamplona/amethyst/compare/v0.83.5...v0.83.7
[v0.83.5]: https://github.com/vitorpamplona/amethyst/compare/v0.83.4...v0.83.5
[v0.83.4]: https://github.com/vitorpamplona/amethyst/compare/v0.83.3...v0.83.4
[v0.83.3]: https://github.com/vitorpamplona/amethyst/compare/v0.83.1...v0.83.3
[v0.83.1]: https://github.com/vitorpamplona/amethyst/compare/v0.82.3...v0.83.1
[v0.82.3]: https://github.com/vitorpamplona/amethyst/compare/v0.82.2...v0.82.3
[v0.82.2]: https://github.com/vitorpamplona/amethyst/compare/v0.82.1...v0.82.2
[v0.82.1]: https://github.com/vitorpamplona/amethyst/compare/v0.81.5...v0.82.1
[v0.81.5]: https://github.com/vitorpamplona/amethyst/compare/v0.81.3...v0.81.5
[v0.81.3]: https://github.com/vitorpamplona/amethyst/compare/v0.80.7...v0.81.3
[v0.80.7]: https://github.com/vitorpamplona/amethyst/compare/v0.80.4...v0.80.7
[v0.80.4]: https://github.com/vitorpamplona/amethyst/compare/v0.80.2...v0.80.4
[v0.80.2]: https://github.com/vitorpamplona/amethyst/compare/v0.80.1...v0.80.2
[v0.80.1]: https://github.com/vitorpamplona/amethyst/compare/v0.79.13...v0.80.1
[v0.79.13]: https://github.com/vitorpamplona/amethyst/compare/v0.79.12...v0.79.13
[v0.79.12]: https://github.com/vitorpamplona/amethyst/compare/v0.79.10...v0.79.12
[v0.79.10]: https://github.com/vitorpamplona/amethyst/compare/v0.79.9...v0.79.10
[v0.79.9]: https://github.com/vitorpamplona/amethyst/compare/v0.79.7...v0.79.9
[v0.79.7]: https://github.com/vitorpamplona/amethyst/compare/v0.79.6...v0.79.7
[v0.79.6]: https://github.com/vitorpamplona/amethyst/compare/v0.79.5...v0.79.6
[v0.79.5]: https://github.com/vitorpamplona/amethyst/compare/v0.79.2...v0.79.5
[v0.79.2]: https://github.com/vitorpamplona/amethyst/compare/v0.79.0...v0.79.2
[v0.79.0]: https://github.com/vitorpamplona/amethyst/compare/v0.78.2...v0.79.0
[v0.78.2]: https://github.com/vitorpamplona/amethyst/compare/v0.78.1...v0.78.2
[v0.78.1]: https://github.com/vitorpamplona/amethyst/compare/v0.78.0...v0.78.1
[v0.78.0]: https://github.com/vitorpamplona/amethyst/compare/v0.77.8...v0.78.0
[v0.77.8]: https://github.com/vitorpamplona/amethyst/compare/v0.77.7...v0.77.8
[v0.77.7]: https://github.com/vitorpamplona/amethyst/compare/v0.77.5...v0.77.7
[v0.77.5]: https://github.com/vitorpamplona/amethyst/compare/v0.77.3...v0.77.5
[v0.77.3]: https://github.com/vitorpamplona/amethyst/compare/v0.77.1...v0.77.3
[v0.77.1]: https://github.com/vitorpamplona/amethyst/compare/v0.77.0...v0.77.1
[v0.77.0]: https://github.com/vitorpamplona/amethyst/compare/v0.76.2...v0.77.0
[v0.76.2]: https://github.com/vitorpamplona/amethyst/compare/v0.76.1...v0.76.2
[v0.76.1]: https://github.com/vitorpamplona/amethyst/compare/v0.76.0...v0.76.1
[v0.76.0]: https://github.com/vitorpamplona/amethyst/compare/v0.75.14...v0.76.0
[v0.75.14]: https://github.com/vitorpamplona/amethyst/compare/v0.75.13...v0.75.14
[v0.75.13]: https://github.com/vitorpamplona/amethyst/compare/v0.75.12...v0.75.13
[v0.75.12]: https://github.com/vitorpamplona/amethyst/compare/v0.75.11...v0.75.12
[v0.75.11]: https://github.com/vitorpamplona/amethyst/compare/v0.75.10...v0.75.11
[v0.75.10]: https://github.com/vitorpamplona/amethyst/compare/v0.75.8...v0.75.10
[v0.75.8]: https://github.com/vitorpamplona/amethyst/compare/v0.75.7...v0.75.8
[v0.75.7]: https://github.com/vitorpamplona/amethyst/compare/v0.75.6...v0.75.7
[v0.75.6]: https://github.com/vitorpamplona/amethyst/compare/v0.75.3...v0.75.6
[v0.75.3]: https://github.com/vitorpamplona/amethyst/compare/v0.75.2...v0.75.3
[v0.75.2]: https://github.com/vitorpamplona/amethyst/compare/v0.75.1...v0.75.2
[v0.75.1]: https://github.com/vitorpamplona/amethyst/compare/v0.75.0...v0.75.1
[v0.75.0]: https://github.com/vitorpamplona/amethyst/compare/v0.74.5...v0.75.0
[v0.74.5]: https://github.com/vitorpamplona/amethyst/compare/v0.74.4...v0.74.5
[v0.74.4]: https://github.com/vitorpamplona/amethyst/compare/v0.74.3...v0.74.4
[v0.74.3]: https://github.com/vitorpamplona/amethyst/compare/v0.74.2...v0.74.3
[v0.74.2]: https://github.com/vitorpamplona/amethyst/compare/v0.73.3...v0.74.2
[v0.73.3]: https://github.com/vitorpamplona/amethyst/compare/v0.73.2...v0.73.3
[v0.73.2]: https://github.com/vitorpamplona/amethyst/compare/v0.73.1...v0.73.2
[v0.73.1]: https://github.com/vitorpamplona/amethyst/compare/v0.72.2...v0.73.1
[v0.72.2]: https://github.com/vitorpamplona/amethyst/compare/v0.72.1...v0.72.2
[v0.72.1]: https://github.com/vitorpamplona/amethyst/compare/v0.72.0...v0.72.1
[v0.72.0]: https://github.com/vitorpamplona/amethyst/compare/v0.71.0...v0.72.0
[v0.71.0]: https://github.com/vitorpamplona/amethyst/compare/v0.70.8...v0.71.0
[v0.70.8]: https://github.com/vitorpamplona/amethyst/compare/v0.70.7...v0.70.8
[v0.70.7]: https://github.com/vitorpamplona/amethyst/compare/v0.70.6...v0.70.7
[v0.70.6]: https://github.com/vitorpamplona/amethyst/compare/v0.70.5...v0.70.6
[v0.70.5]: https://github.com/vitorpamplona/amethyst/compare/v0.70.4...v0.70.5
[v0.70.4]: https://github.com/vitorpamplona/amethyst/compare/v0.70.3...v0.70.4
[v0.70.3]: https://github.com/vitorpamplona/amethyst/compare/v0.70.2...v0.70.3
[v0.70.2]: https://github.com/vitorpamplona/amethyst/compare/v0.70.1...v0.70.2
[v0.70.1]: https://github.com/vitorpamplona/amethyst/compare/v0.70.0...v0.70.1
[v0.70.0]: https://github.com/vitorpamplona/amethyst/compare/v0.69.3...v0.70.0
[v0.69.3]: https://github.com/vitorpamplona/amethyst/compare/v0.69.2...v0.69.3
[v0.69.2]: https://github.com/vitorpamplona/amethyst/compare/v0.69.1...v0.69.2
[v0.69.1]: https://github.com/vitorpamplona/amethyst/compare/v0.69.0...v0.69.1
[v0.69.0]: https://github.com/vitorpamplona/amethyst/compare/v0.68.4...v0.69.0
[v0.68.4]: https://github.com/vitorpamplona/amethyst/compare/v0.68.3...v0.68.4
[v0.68.3]: https://github.com/vitorpamplona/amethyst/compare/v0.68.2...v0.68.3
[v0.68.2]: https://github.com/vitorpamplona/amethyst/compare/v0.68.1...v0.68.2
[v0.68.1]: https://github.com/vitorpamplona/amethyst/compare/v0.68.0...v0.68.1
[v0.68.0]: https://github.com/vitorpamplona/amethyst/compare/v0.67.1...v0.68.0
[v0.67.1]: https://github.com/vitorpamplona/amethyst/compare/v0.67.0...v0.67.1
[v0.67.0]: https://github.com/vitorpamplona/amethyst/compare/v0.66.7...v0.67.0
[v0.66.7]: https://github.com/vitorpamplona/amethyst/compare/v0.66.6...v0.66.7
[v0.66.6]: https://github.com/vitorpamplona/amethyst/compare/v0.66.5...v0.66.6
[v0.66.5]: https://github.com/vitorpamplona/amethyst/compare/v0.66.4...v0.66.5
[v0.66.4]: https://github.com/vitorpamplona/amethyst/compare/v0.66.3...v0.66.4
[v0.66.3]: https://github.com/vitorpamplona/amethyst/compare/v0.66.2...v0.66.3
[v0.66.2]: https://github.com/vitorpamplona/amethyst/compare/v0.66.1...v0.66.2
[v0.66.1]: https://github.com/vitorpamplona/amethyst/compare/v0.66.0...v0.66.1
[v0.66.0]: https://github.com/vitorpamplona/amethyst/compare/v0.65.1...v0.66.0
[v0.65.1]: https://github.com/vitorpamplona/amethyst/compare/v0.65.0...v0.65.1
[v0.65.0]: https://github.com/vitorpamplona/amethyst/compare/v0.64.4...v0.65.0
[v0.64.4]: https://github.com/vitorpamplona/amethyst/compare/v0.64.3...v0.64.4
[v0.64.3]: https://github.com/vitorpamplona/amethyst/compare/v0.64.2...v0.64.3
[v0.64.2]: https://github.com/vitorpamplona/amethyst/compare/v0.64.0...v0.64.2
[v0.64.0]: https://github.com/vitorpamplona/amethyst/compare/v0.63.0...v0.64.0
[v0.63.0]: https://github.com/vitorpamplona/amethyst/compare/v0.62.8...v0.63.0
[v0.62.8]: https://github.com/vitorpamplona/amethyst/compare/v0.62.7...v0.62.8
[v0.62.7]: https://github.com/vitorpamplona/amethyst/compare/v0.62.6...v0.62.7
[v0.62.6]: https://github.com/vitorpamplona/amethyst/compare/v0.62.5...v0.62.6
[v0.62.5]: https://github.com/vitorpamplona/amethyst/compare/v0.62.4...v0.62.5
[v0.62.4]: https://github.com/vitorpamplona/amethyst/compare/v0.62.3...v0.62.4
[v0.62.3]: https://github.com/vitorpamplona/amethyst/compare/v0.62.2...v0.62.3
[v0.62.2]: https://github.com/vitorpamplona/amethyst/compare/v0.62.1...v0.62.2
[v0.62.1]: https://github.com/vitorpamplona/amethyst/compare/v0.62.0...v0.62.1
[v0.62.0]: https://github.com/vitorpamplona/amethyst/compare/v0.61.4...v0.62.0
[v0.61.4]: https://github.com/vitorpamplona/amethyst/compare/v0.61.3...v0.61.4
[v0.61.3]: https://github.com/vitorpamplona/amethyst/compare/v0.61.2...v0.61.3
[v0.61.2]: https://github.com/vitorpamplona/amethyst/compare/v0.61.1...v0.61.2
[v0.61.1]: https://github.com/vitorpamplona/amethyst/compare/v0.61.0...v0.61.1
[v0.61.0]: https://github.com/vitorpamplona/amethyst/compare/v0.60.2...v0.61.0
[v0.60.2]: https://github.com/vitorpamplona/amethyst/compare/v0.60.1...v0.60.2
[v0.60.1]: https://github.com/vitorpamplona/amethyst/compare/v0.60.0...v0.60.1
[v0.60.0]: https://github.com/vitorpamplona/amethyst/compare/v0.59.1...v0.60.0
[v0.59.1]: https://github.com/vitorpamplona/amethyst/compare/v0.58.3...v0.59.1
[v0.58.3]: https://github.com/vitorpamplona/amethyst/compare/v0.58.2...v0.58.3
[v0.58.2]: https://github.com/vitorpamplona/amethyst/compare/v0.58.1...v0.58.2
[v0.58.1]: https://github.com/vitorpamplona/amethyst/compare/v0.58.0...v0.58.1
[v0.58.0]: https://github.com/vitorpamplona/amethyst/compare/v0.57.0...v0.58.0
[v0.57.0]: https://github.com/vitorpamplona/amethyst/compare/v0.56.5...v0.57.0
[v0.56.5]: https://github.com/vitorpamplona/amethyst/compare/v0.56.4...v0.56.5
[v0.56.4]: https://github.com/vitorpamplona/amethyst/compare/v0.56.3...v0.56.4
[v0.56.3]: https://github.com/vitorpamplona/amethyst/compare/v0.56.2...v0.56.3
[v0.56.2]: https://github.com/vitorpamplona/amethyst/compare/v0.56.1...v0.56.2
[v0.56.1]: https://github.com/vitorpamplona/amethyst/compare/v0.56.0...v0.56.1
[v0.56.0]: https://github.com/vitorpamplona/amethyst/compare/v0.55.4...v0.56.0
[v0.55.4]: https://github.com/vitorpamplona/amethyst/compare/v0.55.3...v0.55.4
[v0.55.3]: https://github.com/vitorpamplona/amethyst/compare/v0.55.2...v0.55.3
[v0.55.2]: https://github.com/vitorpamplona/amethyst/compare/v0.55.1...v0.55.2
[v0.55.1]: https://github.com/vitorpamplona/amethyst/compare/v0.55.0...v0.55.1
[v0.55.0]: https://github.com/vitorpamplona/amethyst/compare/v0.54.2...v0.55.0
[v0.54.2]: https://github.com/vitorpamplona/amethyst/compare/v0.54.1...v0.54.2
[v0.54.1]: https://github.com/vitorpamplona/amethyst/compare/v0.54.0...v0.54.1
[v0.54.0]: https://github.com/vitorpamplona/amethyst/compare/v0.53.7...v0.54.0
[v0.53.7]: https://github.com/vitorpamplona/amethyst/compare/v0.53.6...v0.53.7
[v0.53.6]: https://github.com/vitorpamplona/amethyst/compare/v0.53.5...v0.53.6
[v0.53.5]: https://github.com/vitorpamplona/amethyst/compare/v0.53.4...v0.53.5
[v0.53.4]: https://github.com/vitorpamplona/amethyst/compare/v0.53.3...v0.53.4
[v0.53.3]: https://github.com/vitorpamplona/amethyst/compare/v0.53.2...v0.53.3
[v0.53.2]: https://github.com/vitorpamplona/amethyst/compare/v0.53.1...v0.53.2
[v0.53.1]: https://github.com/vitorpamplona/amethyst/compare/v0.53.0...v0.53.1
[v0.53.0]: https://github.com/vitorpamplona/amethyst/compare/v0.52.3...v0.53.0
[v0.52.3]: https://github.com/vitorpamplona/amethyst/compare/v0.52.2...v0.52.3
[v0.52.2]: https://github.com/vitorpamplona/amethyst/compare/v0.52.1...v0.52.2
[v0.52.1]: https://github.com/vitorpamplona/amethyst/compare/v0.52.0...v0.52.1
[v0.52.0]: https://github.com/vitorpamplona/amethyst/compare/v0.51.4...v0.52.0
[v0.51.4]: https://github.com/vitorpamplona/amethyst/compare/v0.51.3...v0.51.4
[v0.51.3]: https://github.com/vitorpamplona/amethyst/compare/v0.51.2...v0.51.3
[v0.51.2]: https://github.com/vitorpamplona/amethyst/compare/v0.51.1...v0.51.2
[v0.51.1]: https://github.com/vitorpamplona/amethyst/compare/v0.51.0...v0.51.1
[v0.51.0]: https://github.com/vitorpamplona/amethyst/compare/v0.50.6...v0.51.0
[v0.50.6]: https://github.com/vitorpamplona/amethyst/compare/v0.50.5...v0.50.6
[v0.50.5]: https://github.com/vitorpamplona/amethyst/compare/v0.50.4...v0.50.5
[v0.50.4]: https://github.com/vitorpamplona/amethyst/compare/v0.50.3...v0.50.4
[v0.50.3]: https://github.com/vitorpamplona/amethyst/compare/v0.50.2...v0.50.3
[v0.50.2]: https://github.com/vitorpamplona/amethyst/compare/v0.50.1...v0.50.2
[v0.50.1]: https://github.com/vitorpamplona/amethyst/compare/v0.50.0...v0.50.1
[v0.50.0]: https://github.com/vitorpamplona/amethyst/compare/v0.49.4...v0.50.0
[v0.49.4]: https://github.com/vitorpamplona/amethyst/compare/v0.49.3...v0.49.4
[v0.49.3]: https://github.com/vitorpamplona/amethyst/compare/v0.49.2...v0.49.3
[v0.49.2]: https://github.com/vitorpamplona/amethyst/compare/v0.49.1...v0.49.2
[v0.49.1]: https://github.com/vitorpamplona/amethyst/compare/v0.49.0...v0.49.1
[v0.49.0]: https://github.com/vitorpamplona/amethyst/compare/v0.48.0...v0.49.0
[v0.48.0]: https://github.com/vitorpamplona/amethyst/compare/v0.47.0...v0.48.0
[v0.47.0]: https://github.com/vitorpamplona/amethyst/compare/v0.46.6...v0.47.0
[v0.46.6]: https://github.com/vitorpamplona/amethyst/compare/v0.46.5...v0.46.6
[v0.46.5]: https://github.com/vitorpamplona/amethyst/compare/v0.46.4...v0.46.5
[v0.46.4]: https://github.com/vitorpamplona/amethyst/compare/v0.46.3...v0.46.4
[v0.46.3]: https://github.com/vitorpamplona/amethyst/compare/v0.46.2...v0.46.3
[v0.46.2]: https://github.com/vitorpamplona/amethyst/compare/v0.46.1...v0.46.2
[v0.46.1]: https://github.com/vitorpamplona/amethyst/compare/v0.46.0...v0.46.1
[v0.46.0]: https://github.com/vitorpamplona/amethyst/compare/v0.45.1...v0.46.0
[v0.45.1]: https://github.com/vitorpamplona/amethyst/compare/v0.45.0...v0.45.1
[v0.45.0]: https://github.com/vitorpamplona/amethyst/compare/v0.44.0...v0.45.0
[v0.44.0]: https://github.com/vitorpamplona/amethyst/compare/v0.43.2...v0.44.0
[v0.43.2]: https://github.com/vitorpamplona/amethyst/compare/v0.43.1...v0.43.2
[v0.43.1]: https://github.com/vitorpamplona/amethyst/compare/v0.43.0...v0.43.1
[v0.43.0]: https://github.com/vitorpamplona/amethyst/compare/v0.42.5...v0.43.0
[v0.42.5]: https://github.com/vitorpamplona/amethyst/compare/v0.42.4...v0.42.5
[v0.42.4]: https://github.com/vitorpamplona/amethyst/compare/v0.42.2...v0.42.4
[v0.42.2]: https://github.com/vitorpamplona/amethyst/compare/v0.42.1...v0.42.2
[v0.42.1]: https://github.com/vitorpamplona/amethyst/compare/v0.42.0...v0.42.1
[v0.42.0]: https://github.com/vitorpamplona/amethyst/compare/v0.41.0...v0.42.0
[v0.41.0]: https://github.com/vitorpamplona/amethyst/compare/v0.40.6...v0.41.0
[v0.40.6]: https://github.com/vitorpamplona/amethyst/compare/v0.40.5...v0.40.6
[v0.40.5]: https://github.com/vitorpamplona/amethyst/compare/v0.40.4...v0.40.5
[v0.40.4]: https://github.com/vitorpamplona/amethyst/compare/v0.40.3...v0.40.4
[v0.40.3]: https://github.com/vitorpamplona/amethyst/compare/v0.40.2...v0.40.3
[v0.40.2]: https://github.com/vitorpamplona/amethyst/compare/v0.40.1...v0.40.2
[v0.40.1]: https://github.com/vitorpamplona/amethyst/compare/v0.40.0...v0.40.1
[v0.40.0]: https://github.com/vitorpamplona/amethyst/compare/v0.38.0...v0.40.0
[v0.38.0]: https://github.com/vitorpamplona/amethyst/compare/v0.37.4...v0.38.0
[v0.37.4]: https://github.com/vitorpamplona/amethyst/compare/v0.37.3...v0.37.4
[v0.37.3]: https://github.com/vitorpamplona/amethyst/compare/v0.37.2...v0.37.3
[v0.37.2]: https://github.com/vitorpamplona/amethyst/compare/v0.37.1...v0.37.2
[v0.37.1]: https://github.com/vitorpamplona/amethyst/compare/v0.37.0...v0.37.1
[v0.37.0]: https://github.com/vitorpamplona/amethyst/compare/v0.36.0...v0.37.0
[v0.36.0]: https://github.com/vitorpamplona/amethyst/compare/v0.35.1...v0.36.0
[v0.35.1]: https://github.com/vitorpamplona/amethyst/compare/v0.35.0...v0.35.1
[v0.35.0]: https://github.com/vitorpamplona/amethyst/compare/v0.34.1...v0.35.0
[v0.34.1]: https://github.com/vitorpamplona/amethyst/compare/v0.34.0...v0.34.1
[v0.34.0]: https://github.com/vitorpamplona/amethyst/compare/v0.33.2...v0.34.0
[v0.33.2]: https://github.com/vitorpamplona/amethyst/compare/v0.33.1...v0.33.2
[v0.33.1]: https://github.com/vitorpamplona/amethyst/compare/v0.33.0...v0.33.1
[v0.33.0]: https://github.com/vitorpamplona/amethyst/compare/v0.32.3...v0.33.0
[v0.32.3]: https://github.com/vitorpamplona/amethyst/compare/v0.32.2...v0.32.3
[v0.32.2]: https://github.com/vitorpamplona/amethyst/compare/v0.32.1...v0.32.2
[v0.32.1]: https://github.com/vitorpamplona/amethyst/compare/v0.32.0...v0.32.1
[v0.32.0]: https://github.com/vitorpamplona/amethyst/compare/v0.31.4...v0.32.0
[v0.31.4]: https://github.com/vitorpamplona/amethyst/compare/v0.31.3...v0.31.4
[v0.31.3]: https://github.com/vitorpamplona/amethyst/compare/v0.31.2...v0.31.3
[v0.31.2]: https://github.com/vitorpamplona/amethyst/compare/v0.31.0...v0.31.2
[v0.31.0]: https://github.com/vitorpamplona/amethyst/compare/v0.30.2...v0.31.0
[v0.30.2]: https://github.com/vitorpamplona/amethyst/compare/v0.30.1...v0.30.2
[v0.30.1]: https://github.com/vitorpamplona/amethyst/compare/v0.30.0...v0.30.1
[v0.30.0]: https://github.com/vitorpamplona/amethyst/compare/v0.29.2...v0.30.0
[v0.29.2]: https://github.com/vitorpamplona/amethyst/compare/v0.29.1...v0.29.2
[v0.29.1]: https://github.com/vitorpamplona/amethyst/compare/v0.29.0...v0.29.1
[v0.29.0]: https://github.com/vitorpamplona/amethyst/compare/v0.28.1...v0.29.0
[v0.28.1]: https://github.com/vitorpamplona/amethyst/compare/v0.28.0...v0.28.1
[v0.28.0]: https://github.com/vitorpamplona/amethyst/compare/v0.27.2...v0.28.0
[v0.27.2]: https://github.com/vitorpamplona/amethyst/compare/v0.27.1...v0.27.2
[v0.27.1]: https://github.com/vitorpamplona/amethyst/compare/v0.27.0...v0.27.1
[v0.27.0]: https://github.com/vitorpamplona/amethyst/compare/v0.26.2...v0.27.0
[v0.26.2]: https://github.com/vitorpamplona/amethyst/compare/v0.26.1...v0.26.2
[v0.26.1]: https://github.com/vitorpamplona/amethyst/compare/v0.26.0...v0.26.1
[v0.26.0]: https://github.com/vitorpamplona/amethyst/compare/v0.25.3...v0.26.0
[v0.25.3]: https://github.com/vitorpamplona/amethyst/compare/v0.25.2...v0.25.3
[v0.25.2]: https://github.com/vitorpamplona/amethyst/compare/v0.25.1...v0.25.2
[v0.25.1]: https://github.com/vitorpamplona/amethyst/compare/v0.25.0...v0.25.1
[v0.25.0]: https://github.com/vitorpamplona/amethyst/compare/v0.24.2...v0.25.0
[v0.24.2]: https://github.com/vitorpamplona/amethyst/compare/v0.24.1...v0.24.2
[v0.24.1]: https://github.com/vitorpamplona/amethyst/compare/v0.24.0...v0.24.1
[v0.24.0]: https://github.com/vitorpamplona/amethyst/compare/v0.23.1...v0.24.0
[v0.23.1]: https://github.com/vitorpamplona/amethyst/compare/v0.23.0...v0.23.1
[v0.23.0]: https://github.com/vitorpamplona/amethyst/compare/v0.22.3...v0.23.0
[v0.22.3]: https://github.com/vitorpamplona/amethyst/compare/v0.22.2...v0.22.3
[v0.22.2]: https://github.com/vitorpamplona/amethyst/compare/v0.22.1...v0.22.2
[v0.22.1]: https://github.com/vitorpamplona/amethyst/compare/v0.22.0...v0.22.1
[v0.22.0]: https://github.com/vitorpamplona/amethyst/compare/v0.21.2...v0.22.0
[v0.21.2]: https://github.com/vitorpamplona/amethyst/compare/v0.21.1...v0.21.2
[v0.21.1]: https://github.com/vitorpamplona/amethyst/compare/v0.20.5...v0.21.1
[v0.20.5]: https://github.com/vitorpamplona/amethyst/compare/v0.20.4...v0.20.5
[v0.20.4]: https://github.com/vitorpamplona/amethyst/compare/v0.20.3...v0.20.4
[v0.20.3]: https://github.com/vitorpamplona/amethyst/compare/v0.20.2...v0.20.3
[v0.20.2]: https://github.com/vitorpamplona/amethyst/compare/v0.20.1...v0.20.2
[v0.20.1]: https://github.com/vitorpamplona/amethyst/compare/v0.20.0...v0.20.1
[v0.20.0]: https://github.com/vitorpamplona/amethyst/compare/v0.19.1...v0.20.0
[v0.19.1]: https://github.com/vitorpamplona/amethyst/compare/v0.19.0...v0.19.1
[v0.19.0]: https://github.com/vitorpamplona/amethyst/compare/v0.18.3...v0.19.0
[v0.18.3]: https://github.com/vitorpamplona/amethyst/compare/v0.18.2...v0.18.3
[v0.18.2]: https://github.com/vitorpamplona/amethyst/compare/v0.18.1...v0.18.2
[v0.18.1]: https://github.com/vitorpamplona/amethyst/compare/v0.18.0...v0.18.1
[v0.18.0]: https://github.com/vitorpamplona/amethyst/compare/v0.17.11...v0.18.0
[v0.17.11]: https://github.com/vitorpamplona/amethyst/compare/v0.17.10...v0.17.11
[v0.17.10]: https://github.com/vitorpamplona/amethyst/compare/v0.17.9...v0.17.10
[v0.17.9]: https://github.com/vitorpamplona/amethyst/compare/v0.17.8...v0.17.9
[v0.17.8]: https://github.com/vitorpamplona/amethyst/compare/v0.17.7...v0.17.8
[v0.17.7]: https://github.com/vitorpamplona/amethyst/compare/v0.17.6...v0.17.7
[v0.17.6]: https://github.com/vitorpamplona/amethyst/compare/v0.17.5...v0.17.6
[v0.17.5]: https://github.com/vitorpamplona/amethyst/compare/v0.17.4...v0.17.5
[v0.17.4]: https://github.com/vitorpamplona/amethyst/compare/v0.17.3...v0.17.4
[v0.17.3]: https://github.com/vitorpamplona/amethyst/compare/v0.17.2...v0.17.3
[v0.17.2]: https://github.com/vitorpamplona/amethyst/compare/v0.17.1...v0.17.2
[v0.17.1]: https://github.com/vitorpamplona/amethyst/compare/v0.17.0...v0.17.1
[v0.17.0]: https://github.com/vitorpamplona/amethyst/compare/v0.16.2...v0.17.0
[v0.16.2]: https://github.com/vitorpamplona/amethyst/compare/v0.16.1...v0.16.2
[v0.16.1]: https://github.com/vitorpamplona/amethyst/compare/v0.16.0...v0.16.1
[v0.16.0]: https://github.com/vitorpamplona/amethyst/compare/v0.15.9...v0.16.0
[v0.15.9]: https://github.com/vitorpamplona/amethyst/compare/v0.15.8...v0.15.9
[v0.15.8]: https://github.com/vitorpamplona/amethyst/compare/v0.15.7...v0.15.8
[v0.15.7]: https://github.com/vitorpamplona/amethyst/compare/v0.15.6...v0.15.7
[v0.15.6]: https://github.com/vitorpamplona/amethyst/compare/v0.15.5...v0.15.6
[v0.15.5]: https://github.com/vitorpamplona/amethyst/compare/v0.15.4...v0.15.5
[v0.15.4]: https://github.com/vitorpamplona/amethyst/compare/v0.15.3...v0.15.4
[v0.15.3]: https://github.com/vitorpamplona/amethyst/compare/v0.15.2...v0.15.3
[v0.15.2]: https://github.com/vitorpamplona/amethyst/compare/v0.15.1...v0.15.2
[v0.15.1]: https://github.com/vitorpamplona/amethyst/compare/v0.14.3...v0.15.1
[v0.14.3]: https://github.com/vitorpamplona/amethyst/compare/v0.14.2...v0.14.3
[v0.14.2]: https://github.com/vitorpamplona/amethyst/compare/v0.14.1...v0.14.2
[v0.14.1]: https://github.com/vitorpamplona/amethyst/compare/v0.14.0...v0.14.1
[v0.14.0]: https://github.com/vitorpamplona/amethyst/compare/v0.13.3...v0.14.0
[v0.13.3]: https://github.com/vitorpamplona/amethyst/compare/v0.13.2...v0.13.3
[v0.13.2]: https://github.com/vitorpamplona/amethyst/compare/v0.13.1...v0.13.2
[v0.13.1]: https://github.com/vitorpamplona/amethyst/compare/v0.13.0...v0.13.1
[v0.13.0]: https://github.com/vitorpamplona/amethyst/compare/v0.12.1...v0.13.0
[v0.12.1]: https://github.com/vitorpamplona/amethyst/compare/v0.12.0...v0.12.1
[v0.12.0]: https://github.com/vitorpamplona/amethyst/compare/v0.11.7...v0.12.0
[v0.11.7]: https://github.com/vitorpamplona/amethyst/compare/v0.11.6...v0.11.7
[v0.11.6]: https://github.com/vitorpamplona/amethyst/compare/v0.11.5...v0.11.6
[v0.11.5]: https://github.com/vitorpamplona/amethyst/compare/v0.11.4...v0.11.5
[v0.11.4]: https://github.com/vitorpamplona/amethyst/compare/v0.11.3...v0.11.4
[v0.11.3]: https://github.com/vitorpamplona/amethyst/compare/v0.11.2...v0.11.3
[v0.11.2]: https://github.com/vitorpamplona/amethyst/compare/v0.11.1...v0.11.2
[v0.11.1]: https://github.com/vitorpamplona/amethyst/compare/v0.11.0...v0.11.1
[v0.11.0]: https://github.com/vitorpamplona/amethyst/compare/v0.10.7...v0.11.0
[v0.10.7]: https://github.com/vitorpamplona/amethyst/compare/v0.10.6...v0.10.7
[v0.10.6]: https://github.com/vitorpamplona/amethyst/compare/v0.10.5...v0.10.6
[v0.10.5]: https://github.com/vitorpamplona/amethyst/compare/v0.10.4...v0.10.5
[v0.10.4]: https://github.com/vitorpamplona/amethyst/compare/v0.10.3...v0.10.4
[v0.10.3]: https://github.com/vitorpamplona/amethyst/compare/v0.10.2...v0.10.3
[v0.10.2]: https://github.com/vitorpamplona/amethyst/compare/v0.10.1...v0.10.2
[v0.10.1]: https://github.com/vitorpamplona/amethyst/compare/v0.10.0...v0.10.1
[v0.10.0]: https://github.com/vitorpamplona/amethyst/compare/v0.9.6...v0.10.0
[v0.9.6]: https://github.com/vitorpamplona/amethyst/compare/v0.9.5...v0.9.6
[v0.9.5]: https://github.com/vitorpamplona/amethyst/compare/v0.9.4...v0.9.5
[v0.9.4]: https://github.com/vitorpamplona/amethyst/compare/v0.9.3...v0.9.4
[v0.9.3]: https://github.com/vitorpamplona/amethyst/compare/v0.9.2...v0.9.3
[v0.9.2]: https://github.com/vitorpamplona/amethyst/compare/v0.9.1...v0.9.2
[v0.9.1]: https://github.com/vitorpamplona/amethyst/compare/v0.9...v0.9.1
[v0.9]: https://github.com/vitorpamplona/amethyst/compare/v0.8.2...v0.9
[v0.8.2]: https://github.com/vitorpamplona/amethyst/compare/v0.8.1...v0.8.2
[v0.8.1]: https://github.com/vitorpamplona/amethyst/compare/v0.8...v0.8.1
[v0.8]: https://github.com/vitorpamplona/amethyst/compare/v0.7...v0.8
[v0.7]: https://github.com/vitorpamplona/amethyst/compare/v0.6...v0.7
[v0.6]: https://github.com/vitorpamplona/amethyst/compare/v0.5...v0.6
[v0.5]: https://github.com/vitorpamplona/amethyst/compare/v0.4...v0.5
[v0.4]: https://github.com/vitorpamplona/amethyst/compare/v0.3...v0.4
[v0.3]: https://github.com/vitorpamplona/amethyst/compare/v0.2...v0.3
[v0.2]: https://github.com/vitorpamplona/amethyst/compare/v0.1...v0.2
[v0.1]: https://github.com/vitorpamplona/amethyst/tree/v0.1