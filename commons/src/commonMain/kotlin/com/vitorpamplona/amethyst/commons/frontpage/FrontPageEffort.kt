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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverParams
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask

/**
 * How hard the writer thinks: the reader's choice, kept in the schedule as the Observer's `effort`
 * param (NIP-FB `param`), so every app, the Assistant and the worker print with it. Standard sends
 * nothing extra and is the param's absence; Deep asks each provider for its high reasoning effort.
 */
enum class WritingEffort(
    /** The param's value; null for the default, which is written as no param at all. */
    val param: String?,
) {
    STANDARD(null),
    DEEP("deep"),
    ;

    companion object {
        /** A param value as an effort: `deep` is Deep, anything else (or none) is Standard. */
        fun of(value: String?): WritingEffort = if (value?.trim().equals(DEEP.param, ignoreCase = true)) DEEP else STANDARD
    }
}

/**
 * The Observer's params beyond Quartz 2.17's [ObserverParams] (`model`, `key`): `effort` and
 * `publish`. Quartz's [ObserverParams.KNOWN] doesn't list them yet, so [ObserverParams.understands]
 * would call such a schedule one this app can't run; these wrap it with both understood.
 */
object FrontPageParams {
    const val EFFORT = "effort"

    /** The schedule's writing effort; Standard when it names none. */
    fun effort(task: ScheduledTask?): WritingEffort = WritingEffort.of(task?.param(EFFORT)?.value())

    /** [task] printing with [effort]: Standard removes the param, so apps that don't know it still run the schedule. */
    fun withEffort(
        task: ScheduledTask,
        effort: WritingEffort,
    ): ScheduledTask = effort.param?.let { task.withParam(EFFORT, it) } ?: task.withoutParam(EFFORT)

    /**
     * Publishing to the reader's public Observer site, for every device of the account
     * (`commons/plans/2026-11-20-observer-synced-settings-and-editions.md`): absent is on and not
     * yet confirmed, `yes` is on and confirmed, `no` (or any other value) is off.
     */
    const val PUBLISH = "publish"
    const val PUBLISH_YES = "yes"
    const val PUBLISH_NO = "no"

    /** The schedule's publishing choice; with no readable schedule, the default (on, not confirmed). */
    fun publishing(task: ScheduledTask?): ObserverPrefs {
        val value =
            task
                ?.param(PUBLISH)
                ?.value()
                ?.trim()
                ?.lowercase() ?: return ObserverPrefs()
        return if (value == PUBLISH_YES) ObserverPrefs(publish = true, noticeSeen = true) else ObserverPrefs(publish = false, noticeSeen = false)
    }

    /** [task] publishing as [prefs] says: off writes `no`, confirmed writes `yes`, on but unconfirmed removes the param. */
    fun withPublishing(
        task: ScheduledTask,
        prefs: ObserverPrefs,
    ): ScheduledTask =
        when {
            !prefs.publish -> task.withParam(PUBLISH, PUBLISH_NO)
            prefs.noticeSeen -> task.withParam(PUBLISH, PUBLISH_YES)
            else -> task.withoutParam(PUBLISH)
        }

    /**
     * Where the paper's weather is for ([ObserverWeather.place]): a name, a latitude and a longitude
     * (to 0.01°), and `us` (°F) or `metric`. Absent, the paper has no weather. A printer that shows
     * no weather still prints: it only decorates the copy shown in the reader's apps.
     */
    const val PLACE = "place"

    /** The params of [task] this app doesn't understand, in order, each once. */
    fun unknownParams(task: ScheduledTask): List<String> = ObserverParams.unknownParams(task).filter { it != EFFORT && it != PUBLISH && it != PLACE }

    /** Whether this app understands every param of [task] (NIP-FB: a task with one it doesn't is never run). */
    fun understands(task: ScheduledTask): Boolean = unknownParams(task).isEmpty()
}
