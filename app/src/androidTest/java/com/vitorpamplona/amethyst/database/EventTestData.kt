package com.vitorpamplona.amethyst.database

import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

val SimpleNoteWithTag = Event(
    id = "5e96f9b3bd4913b0560002d0862358592003c8e82c3722baa9688fbdff6306b1",
    pubKey = "79c2cae114ea28a981e7559b4fe7854a473521a8d22a66bbab9fa248eb820ff6",
    createdAt = 1690374015,
    kind = TextNoteEvent.kind,
    tags = listOf(listOf("mostr", "https://gleasonator.com/objects/c394bc4b-25e6-4475-b6ad-a89cb675a6ee")),
    content = "I already hate trending tags.\n\nhttps://media.gleasonator.com/fab92d4bdc708cf5f457475cd893d40011de5729fa6c1924927a665db5dd73ea.png",
    sig = "450569d2fd8097feb3bf4a04511f3eca5569efe576707219506345445b13fe029218cea87d3796060b7b0d3c014a4d4b0499e56ffd1aebad239ccda4b98f8c6b"
)

val SimpleNoteWithRTag = Event(
    id = "30276d067b4bf0453b9e8f489bbda446340b00c0da2b22ba2e8427705aec76e0",
    pubKey = "00000000827ffaa94bfea288c3dfce4422c794fbb96625b6b31e9049f729d700",
    createdAt = 1690386572,
    kind = TextNoteEvent.kind,
    tags = listOf(listOf("r", "https://youtu.be/FiBiGNrZrsg")),
    content = "https://youtu.be/FiBiGNrZrsg",
    sig = "6860a0339bd15e1b9acb790cbc7dfc47aa162c584d6e47ef2223ac246cbd2c9ead36ca75a45d2f5272d3d0c1908baebc7d5ba712d8b9a0429612b03f4c791bb8"
)

val SimpleNote = Event(
    id = "d963ce47ae5ed5535ffce43c8251a4dd2db2038d5e903136f3734803f7261ff6",
    pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
    createdAt = 1690388339,
    kind = TextNoteEvent.kind,
    tags = listOf(listOf("r", "https://youtu.be/FiBiGNrZrsg")),
    content = "What's the likelihood of relays implementing `g` (Geohash) and `r` (URLs) tag filters as prefixes?",
    sig = "d4c8c61dd057bb7607ac75594589d079175c8d912a62e3c5ff70fd1619f3b7e28e7d18520965a9ace16508c6ad4125205f2ce547c941f986fadd1cd62c342bab"
)
