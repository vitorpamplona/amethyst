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
package com.vitorpamplona.quartz.nip69P2pOrderEvents

/**
 * Events on the P2P-trading kinds and the kinds other apps share with them, fetched from public
 * relays on 2026-10-08. The `REAL_*` ones are signed events copied verbatim (infrastructure data:
 * instance terms, aggregate ratings, fee receipts, order books — no personal text); the
 * `SYNTHETIC_*` ones keep a real event's shape where the real one carried an IP address or a
 * trader's handle, and are unsigned.
 */
object P2PKindFixtures {
    // ---- 38383 ----

    /** A Mostro order. Real shape; synthetic because live orders carry traders' handles in `pm`. */
    const val SYNTHETIC_MOSTRO_ORDER = """{"id":"1111111111111111111111111111111111111111111111111111111111111111","pubkey":"00003f6be51b51a1a0cf9c94232ab1bba1f5c1bfd5a0e8687e9647558536b791","created_at":1791434994,"kind":38383,"tags":[["d","c98f7c86-c1d6-46ac-a858-95e210fa13cd"],["k","buy"],["f","MXN"],["s","canceled"],["amt","0"],["fa","0"],["pm","SPEI"],["premium","-4"],["network","mainnet"],["layer","lightning"],["published_at","1791430910"],["expires_at","1791517310"],["expiration","1794026994"],["y","mostro","Mostro México"],["z","order"]],"content":"","sig":"00"}"""

    /** A RoboSats order: a range `fa`, a decimal `bond` and a `name`. */
    const val REAL_ROBOSATS_ORDER = """{"id":"51df4a199b1406888cbb218da9e39b517c07e2820841562b38051abca1c29a73","pubkey":"e489cdb0b24fa416a49b524625e0edab0f7235f3a0261a3cdfc64c4efdc14afd","created_at":1791413925,"kind":38383,"tags":[["d","robosats:http://2enoseg66hme76khjjn2qvrhipnzwgwa44mewgrdphrxbhzcxd2vdiqd.onion:204"],["k","sell"],["f","USD"],["s","pending"],["amt","0"],["fa","50.00000000","500.00000000"],["premium","4.50"],["source","http://2enoseg66hme76khjjn2qvrhipnzwgwa44mewgrdphrxbhzcxd2vdiqd.onion/order/204"],["network","mainnet"],["layer","lightning"],["name","FreePort"],["bond","3.00"],["expiration","1791442669"],["y","robosats"],["z","order"],["pm","CashApp"]],"content":"","sig":"6a82c7632519126024e0ad3af246e70cf96b8cb3a79ff3c4a51fcdf5f362df76b4962e246a1af98a22ea13899c75cd02572743bf0bd1b3cefdd43fae33b20098"}"""

    /**
     * A Paygress compute offer on 38383 (`src/nostr/subscriber/publish.rs`). Synthetic: the live
     * offer lists mints by IP address.
     */
    const val SYNTHETIC_PAYGRESS_OFFER = """{"id":"2222222222222222222222222222222222222222222222222222222222222222","pubkey":"c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3","created_at":1789923692,"kind":38383,"tags":[["t","paygress"],["t","compute"],["d","paygress:offer:v1:c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3"],["v","1"]],"content":"{\"provider_npub\":\"c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3\",\"hostname\":\"CIRunner\",\"location\":\"VPS\",\"capabilities\":[\"lxc\",\"vm\"],\"images\":[\"paygress-ci\"],\"specs\":[{\"id\":\"basic\",\"name\":\"Basic\",\"description\":\"1 vCPU, 1GB RAM\",\"cpu_millicores\":1000,\"memory_mb\":1024,\"rate_msats_per_sec\":50}],\"whitelisted_mints\":[\"https://mint.example.com\"],\"uptime_percent\":100.0,\"total_jobs_completed\":0,\"api_endpoint\":null,\"version\":1,\"isolation_level\":\"shared-kernel\"}","sig":"00"}"""

    // ---- 38384 ----

    const val REAL_MOSTRO_RATING = """{"id":"58ed04d8678e002b370eefc094a2ac9dab723b6d6035bcae59dde9a1dd347bea","pubkey":"82fa8cb978b43c79b2156585bac2c011176a21d2aead6d9f7c575c005be88390","created_at":1791422814,"kind":38384,"tags":[["d","217004c16c56ff07aa7709ae58e90ccc0d02ba1d2e096c35089055f654eba625"],["expiration","1799198814"],["total_reviews","11"],["total_rating","4.7727272727272725"],["last_rating","5"],["max_rate","5"],["min_rate","5"],["since","1766793600"],["z","rating"],["days","284"]],"content":"","sig":"f2ccdd1754d0259fd46d9f8c9453e78c125cb18cf17b6603cb1be9c2f046275e6d55ded0208fdc8273d07531e45ba9f1d597b536fd1d9096d103537db700b719"}"""

    const val REAL_PAYGRESS_HEARTBEAT = """{"id":"f4fdc9aa4d334c9c16ed41a162269018e71a9fab0678623483b488e8f499cc9a","pubkey":"c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3","created_at":1791429531,"kind":38384,"tags":[["t","paygress-heartbeat"],["d","paygress:heartbeat:v1:c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3:29857158"],["v","1"]],"content":"{\"provider_npub\":\"c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3\",\"timestamp\":1791429531,\"active_workloads\":0,\"available_capacity\":{\"cpu_available\":67000,\"memory_mb_available\":8522,\"storage_gb_available\":125},\"version\":1}","sig":"33a1987d7bdc7bb6ce9367d721aacdd2d583abc9861591df396b396d519c43f5941d4a4e0a756838828f4b97c46d996155ccd1c391081fa9a5e7f6fb0d5fc239"}"""

    /**
     * The heartbeat as Paygress' current code writes it: with a `p` naming the provider. A `p`
     * holding a real pubkey must not make it a rating either.
     */
    const val SYNTHETIC_PAYGRESS_HEARTBEAT_WITH_P = """{"id":"3333333333333333333333333333333333333333333333333333333333333333","pubkey":"c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3","created_at":1791429531,"kind":38384,"tags":[["t","paygress-heartbeat"],["p","c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3"],["d","paygress:heartbeat:v1:c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3:29857158"],["v","1"]],"content":"{\"provider_npub\":\"c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3\",\"timestamp\":1791429531,\"active_workloads\":0,\"available_capacity\":{\"cpu_available\":67000,\"memory_mb_available\":8522,\"storage_gb_available\":125},\"version\":1}","sig":"00"}"""

    // ---- 38385 ----

    /** A Lightning-escrow instance with a bond policy. */
    const val REAL_MOSTRO_INFO_LIGHTNING = """{"id":"e234080f5e6a2eeedc74a6a088ea5070038c2b467604a7455a76446ce863d98c","pubkey":"000018b160c81819d864aa994003b29cdacd6027def22bb5c5002c6a68a3df4b","created_at":1791429549,"kind":38385,"tags":[["d","000018b160c81819d864aa994003b29cdacd6027def22bb5c5002c6a68a3df4b"],["mostro_version","0.18.5"],["mostro_commit_hash","d49064fd4afe32d327d432b88b7a5c2d03606410"],["max_order_amount","800000"],["min_order_amount","100"],["expiration_hours","24"],["expiration_seconds","900"],["fiat_currencies_accepted","USD,EUR,MXN,CUP"],["max_orders_per_response","10"],["fee","0.005"],["pow","0"],["pow_first_contact","0"],["protocol_version","2"],["hold_invoice_expiration_window","300"],["hold_invoice_cltv_delta","144"],["invoice_expiration_window","300"],["lnd_version","0.20.3-beta commit=v0.20.3-beta"],["lnd_node_pubkey","03309907d8d965d930db0aaad834360ce5f9b10a74dac52ea132ea871f809c3c81"],["lnd_commit_hash","05407c84eed9035452c73f4f025efa9f4041ae96"],["lnd_node_alias","CharroNegro"],["lnd_chains","bitcoin"],["lnd_networks","mainnet"],["lnd_uris","03309907d8d965d930db0aaad834360ce5f9b10a74dac52ea132ea871f809c3c81@ps3enjxj6vzabeybz2tcgn5uimknjiox3jrlt76bv2nosdiznlm4flyd.onion:9735"],["y","mostro","CharroNegro"],["z","info"],["bond_enabled","true"],["bond_amount_pct","0.1"],["bond_base_amount_sats","1000"],["bond_apply_to","make"],["bond_slash_on_waiting_timeout","false"],["bond_slash_node_share_pct","0.5"],["bond_payout_claim_window_days","15"]],"content":"","sig":"7b8d3795995265cd79b39f18a949b6041a131e74866b0666098c5f7d595ef73c955841b80844d0ef5a6d04804a3d1cc052f49200d56f967c2c1b2649a2a7449f"}"""

    /** A Cashu-escrow instance with a Serbero. */
    const val REAL_MOSTRO_INFO_CASHU = """{"id":"71fb2bf8ae330a8636a1c26f2ca3dd5f9a0b3f709148d4c418486e95a6250b6a","pubkey":"dbe0b1be7aafd3cfba92d7463edbd4e33b2969f61bd554d37ac56f032e13355a","created_at":1791382331,"kind":38385,"tags":[["d","dbe0b1be7aafd3cfba92d7463edbd4e33b2969f61bd554d37ac56f032e13355a"],["mostro_version","0.19.2"],["mostro_commit_hash","bd6a723248e60dfa8b4657e4c50c38b489ab96ec"],["max_order_amount","1000000"],["min_order_amount","100"],["expiration_hours","1"],["expiration_seconds","900"],["fiat_currencies_accepted","USD,EUR,ARS,CUP,VES"],["max_orders_per_response","100"],["fee","0.01"],["pow","0"],["pow_first_contact","0"],["protocol_version","2"],["y","mostro","Mostro local"],["z","info"],["escrow_mode","cashu"],["cashu_mint_url","https://mint.cubabitcoin.org"],["cashu_escrow_locktime_days","15"],["bond_enabled","false"],["serbero","000005ee0a1a2b3033d2908bb2fc7e29de803d5ac55a249cdebc11d50fca7fc0"],["maintenance_mode","false"]],"content":"","sig":"44ef90af4d6201eb1dd7d9536dc24b0c3f01fcc7cb3140e9c33dd4c6d09448166c821a774b67851367224aca3ba58ac576cec8580f5c44665816e62d020b74b6"}"""

    /** A "bondtrade" bond assignment: `y`/`z` of its own, not Mostro's. */
    const val REAL_BONDTRADE_ASSIGNMENT = """{"id":"20b8b971fb9b0b46f99bd1ae15649f6f028a38a7c9439a0f2c11857178541d23","pubkey":"9effa0c24bc388dbb845520ea40712d70ceebfbaddd9c1eedaadb7556d2f52c3","created_at":1790219233,"kind":38385,"tags":[["d","d66f7e464242a6b9"],["bond","b995324d4db4bc510b751ad5eab70fb4f62c6ebd5b72bf162932d771c6d5d7e5:0"],["role","seller"],["b","b995324d4db4bc510b751ad5eab70fb4f62c6ebd5b72bf162932d771c6d5d7e5:0"],["y","bondtrade"],["z","assignment"]],"content":"{\"bond\":\"b995324d4db4bc510b751ad5eab70fb4f62c6ebd5b72bf162932d771c6d5d7e5:0\",\"trade\":\"d66f7e464242a6b9\"}","sig":"f59dd89f3a166dd561622ad9dfa518ac8b87567086af84c8514257b476f79adf17d3c9a7367b06da36fadb2f082a54e90dfc383aabf9c8e79b72fd5fe2a273f7"}"""

    /** A Paygress lease revocation, as `publish_lease_revocation` writes it. */
    const val SYNTHETIC_PAYGRESS_REVOCATION = """{"id":"4444444444444444444444444444444444444444444444444444444444444444","pubkey":"c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3","created_at":1791429531,"kind":38385,"tags":[["t","paygress"],["t","paygress-revocation"],["d","paygress:revocation:v1:c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3:w-1"],["v","1"],["workload","w-1"],["p","000005ee0a1a2b3033d2908bb2fc7e29de803d5ac55a249cdebc11d50fca7fc0"]],"content":"{\"workload_id\":\"w-1\"}","sig":"00"}"""

    // ---- 38386 ----

    const val REAL_MOSTRO_DISPUTE = """{"id":"2062a939dd3e75d732ecfda2597c3ae4ed6f96ccdf5ef1d48d14501a4f0f8f5e","pubkey":"0000cc02101ec29eea9ce623258752b9d7da66c27845ed26846dd0b0fc736b40","created_at":1791397413,"kind":38386,"tags":[["d","0ab33c54-d9ad-4f7c-b9f3-3257ad81aab6"],["expiration","1799173413"],["s","seller-refunded"],["initiator","seller"],["published_at","1791395845"],["y","mostro","NostroMostro 🇪🇸"],["z","dispute"]],"content":"","sig":"19a8df2867d91135ab255b77ec8558d05625f598f7f9f95ab49df3f532378653e4d6abb7f53707d048b221d7b5f8fdb910528842d38e7bc9d2c6befebf9486a4"}"""

    /** A Paygress standby-promotion announcement, as `publish_standby_promotion_announcement` writes it. */
    const val SYNTHETIC_PAYGRESS_PROMOTION = """{"id":"5555555555555555555555555555555555555555555555555555555555555555","pubkey":"c0ca9e7d4a178bac8a2f0a64f6c8ebcc891e64a6feaf6f3f15e314aa9ff3c7a3","created_at":1791429531,"kind":38386,"tags":[["t","paygress"],["t","paygress-promoted"],["d","paygress:promoted:v1:w-1"],["v","1"],["workload","w-1"]],"content":"{\"workload_id\":\"w-1\"}","sig":"00"}"""

    // ---- 8383 ----

    const val REAL_MOSTRO_DEV_FEE = """{"id":"09f8434a66a1788b17132914610e4fa086ab25d1bd1112bc2836d3b305b617a8","pubkey":"82fa8cb978b43c79b2156585bac2c011176a21d2aead6d9f7c575c005be88390","created_at":1791422826,"kind":8383,"tags":[["order-id","a5febfc3-a23c-47b9-a205-c44f919d7ea8"],["amount","348"],["hash","85bfe082db1a3414d130ac7757be018ec810cd1bca341218f94eae5e933a98f5"],["destination","dev@pay.mostro.foundation"],["network","mainnet"],["y","mostro","Mostro"],["z","dev-fee-payment"],["expiration","1822958826"]],"content":"","sig":"739bf3aa5614d5c4d86f898d32c699fcc178aa8cee0c37a1a2f2483b8be97d2dcac7b030dae7fcb98f274bea82865893084b71e943c8b6a5432c07d07fddbf3a"}"""

    // ---- 31986 ----

    const val REAL_ROBOSATS_RATING = """{"id":"8fcb094d5e4ac8a7602e85c66c80a9673728ec71fa340a2ddd27e3026df92124","pubkey":"96342850bbc9cb0a9ac67d6880c4745971aa19881a3403a297b7330c332ba3cb","created_at":1791409659,"kind":31986,"tags":[["sig","578f47cb33da9bf538db09c6eef357d777523b640898e5f95b28afaa3f5c3701309b743243669cf695dc4e8640af0df59cadd04e2bf08903e9009a405de223b0"],["d","lake:138621"],["p","f2d4855df39a7db6196666e8469a07a131cddc08dcaa744a344343ffcf54a10c"],["rating","1"]],"content":"","sig":"92ac2010a329672f8b376ac1ed4579963e6ddd04899629835cee162500396fa77f4a9bc75e1965eb4f6cffc83f69648796dd18758154ddd3d80717eb21298570"}"""

    /** Borkstr's NIP compatibility report: a `rating` and a `p`, but no coordinator token. */
    const val REAL_BORKSTR_REPORT = """{"id":"be0351c1495f637e7b6646ad0502629b261b69bf169361f79d078c8a0fcbb1cd","pubkey":"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c","created_at":1771091702,"kind":31986,"tags":[["a","31990:460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c:1685802317447"],["p","460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"],["k","31990"],["d","31990:460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c:1685802317447:nip-01"],["rating","1","nip-01"],["L","borkstr"],["l","nip-compatibility","borkstr"],["t","nip-01"],["alt","Borkstr NIP compatibility report"],["client","borkstr-example.shakespeare.wtf"]],"content":"","sig":"97d5f5803a1dcc989f2869183f508429b3c9be6f37792e63bda85b14fc6905cd98ebb4fb9b03cf18ba0dd02eb545bcb0d98bd2c9c5740c30fb29bd9f597b348f"}"""

    val REAL_SIGNED =
        listOf(
            REAL_ROBOSATS_ORDER,
            REAL_MOSTRO_RATING,
            REAL_PAYGRESS_HEARTBEAT,
            REAL_MOSTRO_INFO_LIGHTNING,
            REAL_MOSTRO_INFO_CASHU,
            REAL_BONDTRADE_ASSIGNMENT,
            REAL_MOSTRO_DISPUTE,
            REAL_MOSTRO_DEV_FEE,
            REAL_ROBOSATS_RATING,
            REAL_BORKSTR_REPORT,
        )
}
