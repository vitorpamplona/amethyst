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
package com.vitorpamplona.amethyst.commons.claudecode

import kotlin.test.Test
import kotlin.test.assertTrue

/** What a guarded session's shell commands may do, as a pure function of the command line. */
class SessionCommandGuardTest {
    private fun refused(vararg commands: String) = commands.forEach { assertTrue(SessionCommandGuard.refusal(it) != null, "ran: $it") }

    private fun allowed(vararg commands: String) = commands.forEach { assertTrue(SessionCommandGuard.refusal(it) == null, "refused: $it (${SessionCommandGuard.refusal(it)})") }

    @Test
    fun ghApiWritesAreRefused() =
        refused(
            "gh api repos/o/r/merges -f base=main -f head=x",
            "gh api -X PATCH repos/o/r/git/refs/heads/main -F force=true",
            "gh api --method=PUT repos/o/r/pulls/1/merge",
            "gh api repos/o/r/pulls/1/merge --method PUT",
            "gh api -XPOST repos/o/r/issues",
            "gh api -X put repos/o/r/pulls/1/merge",
            "gh api graphql -f query='mutation { mergePullRequest(input: {}) { clientMutationId } }'",
            "gh api repos/o/r/contents/x --input body.json",
            "gh api repos/o/r/pulls --raw-field title=x",
            "gh api repos/o/r/pulls --field=title=x",
            "gh api repos/o/r/pulls -ftitle=x",
            // A body can't be a read, whatever the method.
            "gh api -X GET repos/o/r/pulls --input body.json",
            "gh api graphql -f query='mutation { mergePullRequest(input: {}) { clientMutationId } }'",
            "gh api graphql -f query='  # merge it\n  mutation { mergePullRequest(input: {}) { clientMutationId } }'",
            "gh api graphql -f query='query A { viewer { login } } mutation B { x }'",
            // A query from a file can't be checked.
            "gh api graphql -F query=@q.graphql",
            "gh api graphql --input q.json",
        )

    @Test
    fun ghApiReadsGo() =
        allowed(
            "gh api repos/o/r/pulls/1/comments",
            "gh api --method GET repos/o/r/pulls",
            "gh api -X GET repos/o/r/pulls",
            "gh api -XGET repos/o/r/pulls",
            "gh api --method=get repos/o/r/pulls",
            "gh api repos/o/r/pulls --jq '.[].title'",
            "gh api -H 'Accept: application/vnd.github+json' --paginate repos/o/r/issues",
            // Fields of an explicit GET are query parameters.
            "gh api -X GET repos/o/r/pulls -F per_page=100",
            "gh api --method GET search/issues -f q='repo:o/r is:open'",
            "gh api graphql -f query='query { viewer { login } }'",
            "gh api graphql -f query='{ repository(owner: \"o\", name: \"r\") { id } }' -f owner=o",
        )

    @Test
    fun ghCommandsThatMergeOrChangeTheRepositoryAreRefused() =
        refused(
            "gh pr merge 1 --squash",
            "gh pr merge 1 -R o/r",
            "gh -R o/r pr merge 1",
            "gh repo edit --default-branch dev",
            "gh repo delete o/r --yes",
            "gh repo rename other",
            "gh repo archive o/r",
            "gh repo sync",
            "gh pr review 1 --approve",
            "gh pr close 1",
            "gh pr edit 1 --base dev",
            "gh pr edit 1 -B dev",
            "gh pr edit 1 --base=dev",
            "gh release create v1",
            "gh workflow run ci.yml",
            "gh workflow enable ci.yml",
            "gh workflow disable ci.yml",
            "gh secret set TOKEN",
            "gh secret list",
            "gh variable set NAME",
            "gh repo create o/new --public",
            "gh repo fork",
            "gh run cancel 1",
            "gh run rerun 1",
        )

    @Test
    fun ghCommandsThatOpenAndReadPullRequestsGo() =
        allowed(
            "gh pr create --title x --body y",
            "gh pr view 1 --comments",
            "gh pr edit 1 --title x --body y",
            "gh pr checks 1",
            "gh pr comment 1 -b 'looks good'",
            "gh pr list --author @me",
            "gh repo view",
        )

    @Test
    fun theCommandIsFoundWhereverItIs() =
        refused(
            "cd x && gh pr merge 1 --squash",
            "/opt/homebrew/bin/gh pr merge 1",
            "GH_REPO=o/r gh pr merge 1",
            "bash -c 'gh pr merge 1'",
            "sh -lc \"gh api -X PUT repos/o/r/pulls/1/merge\"",
            "true; gh api -X DELETE repos/o/r/git/refs/heads/x | cat",
            "echo $(gh api -X PUT repos/o/r/pulls/1/merge)",
            "env FOO=1 gh pr merge 1",
            "command gh pr merge 1",
            "echo \"$(date)\" \"$(gh pr merge 1)\"",
            "echo `date` `gh pr merge 1`",
            "git commit -m \"$(gh pr merge 1)\"",
            "(cd x && gh pr merge 1)",
            "cat <<EOF\n$(gh pr merge 1)\nEOF",
            // A here-string is one word, not a here-document swallowing the lines after it.
            "grep x <<< \"\$v\"\ngh pr merge 1",
            "grep x <<< \"$(gh pr merge 1)\"",
            // In $'…' a \' doesn't end the quote.
            "printf $'a\\'b\\n'; gh pr merge 1",
            "echo $'it\\'s'; gh pr merge 1",
        )

    @Test
    fun textThatOnlyMentionsACommandGoes() =
        allowed(
            "git commit -m 'never run `gh pr merge 1`'",
            "git commit -m 'or $(gh pr merge 1)'",
            "gh pr create --title x --body \"$(cat <<'EOF'\nDon't run `gh pr merge` here (it's the user's).\nEOF\n)\"",
            "gh pr create --title x --body \"$(cat <<\"EOF\"\nthen $(gh pr merge 1)\nEOF\n)\"",
            "cat > notes.md <<EOF\n# Notes\ngh pr merge 1\ngit push --no-verify\nEOF",
            "cat > notes.md <<-'EOF'\n\tgh pr merge 1\n\tEOF\necho done",
            "grep x <<< \"\$v\"",
            "echo $'it\\'s'",
            "echo $'no $(gh pr merge 1) here'",
        )

    @Test
    fun turningThePushGuardOffIsRefused() =
        refused(
            "env -u GIT_CONFIG_COUNT git push origin agent/x",
            "env --unset=GIT_CONFIG_COUNT git push origin agent/x",
            "env -i git push origin agent/x",
            "unset GIT_CONFIG_COUNT; git push origin agent/x",
            "GIT_CONFIG_COUNT=0 git push origin agent/x",
            "export GIT_CONFIG_COUNT=0",
            "export -n GIT_CONFIG_COUNT",
            "declare -x GIT_CONFIG_COUNT=0",
            "env GIT_CONFIG_COUNT=0 git push",
            "env -uGIT_CONFIG_COUNT git push",
            "GIT_CONFIG_PARAMETERS=\"'hook.amethyst-push-guard.enabled'='false'\" git push",
            "git push --no-verify origin agent/x",
            "git push origin agent/x --no-verify",
            "git push --no-veri origin agent/x",
            "git push --no-v origin agent/x",
            "git -c hook.amethyst-push-guard.enabled=false push origin agent/x",
            "git -c hook.pre-push.enabled=false push",
            "git -chook.pre-push.enabled=false push",
            "git --config-env=hook.x.enabled=V push",
            "git --config-env hook.x.enabled=V push",
            "git -c core.hooksPath=/dev/null push",
            "git -c alias.p='push --no-verify' p",
        )

    @Test
    fun ordinaryPushesAndBuildsGo() =
        allowed(
            "git push origin agent/x",
            "git push -u origin HEAD",
            "git push --force-with-lease origin agent/x",
            "git -C sub push origin agent/x",
            "git push --no-thin origin agent/x",
            "git -c user.name=x commit -m 'push --no-verify later'",
            "echo \$GIT_CONFIG_COUNT",
            "git commit -m \"uses GIT_CONFIG\"",
            "grep -rn GIT_CONFIG_COUNT commons/",
            "git log --grep=GIT_CONFIG",
            "printenv | grep GIT_CONFIG",
            "./gradlew :commons:jvmTest",
            "ls -la",
        )

    @Test
    fun ngitMergesAndRepositoryPublishingAreRefused() =
        refused(
            "ngit merge abc",
            "ngit --json pr merge abc",
            "ngit pr close abc",
            "ngit pr applied abc",
            "ngit repo edit --description x",
            "ngit init",
            "ngit sync",
            "ngit release publish",
        )

    @Test
    fun ngitProposalsGo() =
        allowed(
            "ngit send --title x",
            "ngit pr list --json",
            "ngit pr view abc --comments",
            "ngit pr comment abc --body hi",
        )
}
