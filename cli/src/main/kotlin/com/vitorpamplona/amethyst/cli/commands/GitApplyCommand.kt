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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import java.io.File

/**
 * `amy git apply PATCH_ID` — fetch a NIP-34 kind:1617 patch and apply it to the
 * local git working tree (the `nak git patch apply` / `ngit pr apply` surface).
 * The patch content is `git format-patch` output, so by default it is fed to
 * `git am` (applied as a commit); `--check` dry-runs `git apply --check` and
 * `--print` just emits the patch without touching the tree.
 *
 * This shells out to `git`, like `git init` — it operates on the local checkout.
 */
object GitApplyCommand {
    suspend fun apply(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val patchRef = args.positional(0, "patch-event-id")
        val repoDir = File(args.flag("repo") ?: ".").absoluteFile
        val check = args.bool("check")
        val print = args.bool("print")
        val id =
            GitSupport.resolveEventId(patchRef)
                ?: return Output.error("bad_args", "expected a note/nevent/64-hex patch id")
        args.rejectUnknown("relay")

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val patch =
                GitSupport.fetchEvent(ctx, id, args) as? GitPatchEvent
                    ?: return Output.error("not_found", "no patch (kind 1617) found for $patchRef")
            val content = patch.content
            val subject = patch.subject()

            if (print) {
                Output.emit(mapOf("patch_id" to patch.id, "subject" to subject, "content" to content))
                return 0
            }

            val (mode, gitArgs) =
                if (check) {
                    "check" to arrayOf("apply", "--check", "-")
                } else {
                    "am" to arrayOf("am", "--")
                }
            val (code, output) = runGit(repoDir, content, *gitArgs)
            if (code != 0) {
                // Leave the tree clean on a failed `git am` so a retry isn't blocked.
                if (mode == "am") runGit(repoDir, null, "am", "--abort")
                return Output.error(
                    "apply_failed",
                    "git $mode failed for patch ${patch.id}",
                    extra = mapOf("patch_id" to patch.id, "mode" to mode, "git_output" to output.trim()),
                )
            }
            Output.emit(
                mapOf(
                    "patch_id" to patch.id,
                    "subject" to subject,
                    "mode" to mode,
                    "applied" to (mode == "am"),
                    "git_output" to output.trim(),
                ),
            )
            return 0
        }
    }

    /** Run `git <args>` in [repoDir], optionally feeding [input] on stdin; returns (exitCode, merged stdout+stderr). */
    private fun runGit(
        repoDir: File,
        input: String?,
        vararg gitArgs: String,
    ): Pair<Int, String> =
        try {
            val proc =
                ProcessBuilder(listOf("git", *gitArgs))
                    .directory(repoDir)
                    .redirectErrorStream(true)
                    .start()
            if (input != null) proc.outputStream.use { it.write(input.toByteArray()) } else proc.outputStream.close()
            val out = proc.inputStream.readBytes().decodeToString()
            proc.waitFor() to out
        } catch (e: Exception) {
            1 to (e.message ?: "could not run git (is it installed and is this a git repo?)")
        }
}
