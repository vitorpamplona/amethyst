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
package com.vitorpamplona.amethyst.commons.nip34Git

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip34Git.git.GitHttpAuth

/**
 * GRASP-08 auth for the clone URLs of private repositories the cache knows (an announcement
 * tagged `["private", "true"]`): their git requests carry the user's signed `Authorization`;
 * every other repository's go without one, so nothing is signed for a public server.
 */
class GitPrivateRepositoryAuth(
    signer: NostrSigner,
    private val cache: LocalCache = LocalCache,
) : GitHttpAuth(signer) {
    override suspend fun header(cloneUrl: String): String? = if (isPrivate(cloneUrl)) super.header(cloneUrl) else null

    private fun isPrivate(cloneUrl: String): Boolean {
        val wanted = repositoryUrl(cloneUrl).removeSuffix(".git")
        var found = false
        cache.gitRepositories.forEach { _, repository ->
            if (found) return@forEach
            found =
                repository.announcements().values.any { announcement ->
                    announcement.isPrivate() && announcement.clones().any { repositoryUrl(it).removeSuffix(".git") == wanted }
                }
        }
        return found
    }
}
