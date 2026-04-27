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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import androidx.sqlite.SQLiteConnection

suspend fun SQLiteEventStore.explainQuery(
    sql: String,
    args: Array<Any> = emptyArray(),
): String = pool.useReader { it.explainQuery(sql, args.map { a -> a.toString() }.toTypedArray()) }

fun SQLiteConnection.explainQuery(
    sql: String,
    args: Array<String> = emptyArray(),
): String =
    prepare("EXPLAIN QUERY PLAN $sql").use { stmt ->
        args.forEachIndexed { index, arg ->
            stmt.bindText(index + 1, arg)
        }

        val treeIndex = mutableMapOf<Int, PlanNode>()
        val rootNodes = mutableListOf<PlanNode>()

        while (stmt.step()) {
            val id = stmt.getInt(0)
            val parentId = stmt.getInt(1)
            val detail = stmt.getText(3)

            val line = PlanNode(detail)

            treeIndex[id] = line

            val parent = treeIndex[parentId]
            if (parent != null) {
                parent.children.add(line)
            } else {
                rootNodes.add(line)
            }
        }

        buildString {
            appendLine(populateArgs(sql, args))
            for (idx in rootNodes.indices) {
                printNode(rootNodes[idx], "", idx == rootNodes.size - 1, idx > 0)
            }
        }
    }

private fun populateArgs(
    sql: String,
    args: Array<String> = emptyArray(),
): String {
    var result = sql
    args.forEach {
        result = result.replaceFirst("?", "\"$it\"")
    }
    return result
}

fun StringBuilder.printNode(
    node: PlanNode,
    prefix: String,
    isLast: Boolean,
    newLine: Boolean,
) {
    if (newLine) append('\n')
    append(prefix)
    append(if (isLast) "└── " else "├── ")
    append(node.detail)

    val newPrefix = prefix + if (isLast) "    " else "│   "
    for (i in node.children.indices) {
        printNode(node.children[i], newPrefix, i == node.children.size - 1, true)
    }
}

data class PlanNode(
    val detail: String,
    val children: MutableList<PlanNode> = mutableListOf(),
)
