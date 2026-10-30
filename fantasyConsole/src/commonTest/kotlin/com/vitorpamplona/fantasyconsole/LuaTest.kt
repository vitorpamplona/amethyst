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
package com.vitorpamplona.fantasyconsole

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LuaTest {
    @Test
    fun arithmeticIsFixedPoint() {
        val out =
            run(
                """
                printh(1/3)
                printh(5\2)
                printh(-7%3)
                printh(32767+1)
                printh(0x10.8)
                printh(0b101)
                printh(2^10)
                printh(7 & 3)
                printh(5 ^^ 1)
                printh(1 << 4)
                printh(-16 >> 2)
                printh(0x8000 >>> 15)
                printh(~1)
                """.trimIndent(),
            )
        assertEquals(listOf("0.3333", "2", "2", "-32768", "16.5", "5", "1024", "3", "4", "16", "-4", "1", "-1"), out)
    }

    @Test
    fun consoleShorthands() {
        val out =
            run(
                """
                if (true) printh("short if")
                if (false) printh("no") else printh("short else")
                x = 1 x += 2 x *= 3 printh(x)
                s = "a" s ..= "b" printh(s)
                if (1 != 2) printh("ne")
                // a comment
                local n = 0
                while (n < 3) n += 1
                printh(n)
                if (x == 9) then printh("normal if") end
                if (x) and true then printh("and") end
                """.trimIndent(),
            )
        assertEquals(listOf("short if", "short else", "9", "ab", "ne", "3", "normal if", "and"), out)
    }

    @Test
    fun shortIfStopsAtTheEndOfItsLine() {
        val out =
            run(
                """
                function f(a)
                  if (a) return "early"
                  return "late"
                end
                printh(f(true))
                printh(f(false))
                """.trimIndent(),
            )
        assertEquals(listOf("early", "late"), out)
    }

    @Test
    fun closuresCaptureEachIteration() {
        val out =
            run(
                """
                fs = {}
                for i = 1, 3 do add(fs, function() return i end) end
                for f in all(fs) do printh(f()) end
                local function counter()
                  local n = 0
                  return function() n += 1 return n end
                end
                local c = counter()
                c() c()
                printh(c())
                """.trimIndent(),
            )
        assertEquals(listOf("1", "2", "3", "3"), out)
    }

    @Test
    fun varargsAndMultipleReturns() {
        val out =
            run(
                """
                function two() return 1, 2 end
                function count(...) return select("#", ...) end
                printh(count(two()))
                printh(count(two(), 3))
                printh(count((two())))
                local t = {two(), two()}
                printh(#t)
                local a, b, c = two()
                printh(tostr(c))
                printh(select(2, "a", "b", "c"))
                """.trimIndent(),
            )
        assertEquals(listOf("2", "2", "1", "3", "[nil]", "b"), out)
    }

    @Test
    fun metatables() {
        val out =
            run(
                """
                v = {}
                v.__index = v
                v.__add = function(a, b) return setmetatable({x = a.x + b.x}, v) end
                v.__eq = function(a, b) return a.x == b.x end
                v.__lt = function(a, b) return a.x < b.x end
                v.__concat = function(a, b) return "v" .. a.x .. b end
                v.__call = function(self, k) return self.x * k end
                function v.new(x) return setmetatable({x = x}, v) end
                function v:double() return self.x * 2 end
                local a, b = v.new(1), v.new(2)
                printh((a + b).x)
                printh(a:double())
                printh(tostr(v.new(3) == v.new(3)))
                printh(tostr(a < b))
                printh(a .. "!")
                printh(b(10))
                """.trimIndent(),
            )
        assertEquals(listOf("3", "2", "true", "true", "v1!", "20"), out)
    }

    @Test
    fun gotoAndLabels() {
        val out =
            run(
                """
                for i = 1, 4 do
                  if (i % 2 == 0) goto continue
                  printh(i)
                  ::continue::
                end
                local n = 0
                ::again::
                n += 1
                if (n < 3) goto again
                printh(n)
                """.trimIndent(),
            )
        assertEquals(listOf("1", "3", "3"), out)
    }

    @Test
    fun coroutines() {
        val out =
            run(
                """
                local co = cocreate(function(a)
                  local b = yield(a + 1)
                  yield(b * 2)
                  return "done"
                end)
                local ok, v = coresume(co, 1) printh(v)
                ok, v = coresume(co, 5) printh(v)
                ok, v = coresume(co) printh(v)
                printh(costatus(co))
                printh(tostr(coresume(co)))
                local bad = cocreate(function() local x = nil + 1 end)
                local ok2, err = coresume(bad)
                printh(tostr(ok2))
                printh(err)
                """.trimIndent(),
            )
        assertEquals(listOf("2", "10", "done", "dead", "false", "false", "line 11: attempt to perform arithmetic on a nil value"), out)
    }

    @Test
    fun manyResumesInOneFrameKeepAFlatStack() {
        val out =
            run(
                """
                local co = cocreate(function() for i = 1, 20000 do yield(i) end end)
                local last
                while costatus(co) != "dead" do
                  local _, v = coresume(co)
                  if (v) last = v
                end
                printh(last)
                """.trimIndent(),
            )
        assertEquals(listOf("20000"), out)
    }

    @Test
    fun allSurvivesDeletingTheCurrentItem() {
        val out =
            run(
                """
                t = {1, 2, 3, 4, 5}
                for v in all(t) do
                  if (v % 2 == 0) del(t, v)
                end
                local s = ""
                foreach(t, function(v) s ..= v end)
                printh(s)
                printh(count(t))
                deli(t, 1)
                printh(t[1])
                add(t, 9, 1)
                printh(t[1] .. "," .. #t)
                """.trimIndent(),
            )
        assertEquals(listOf("135", "3", "3", "9,3"), out)
    }

    @Test
    fun pairsAndEnv() {
        val out =
            run(
                """
                local t = {10, 20, x = 1}
                local keys = 0
                for k, v in pairs(t) do keys += 1 end
                printh(keys)
                for i, v in ipairs({7, 8}) do printh(i .. "=" .. v) end
                local obj = {hp = 3}
                do
                  local _ENV = obj
                  hp = hp + 1
                end
                printh(obj.hp)
                """.trimIndent(),
            )
        assertEquals(listOf("3", "1=7", "2=8", "4"), out)
    }

    @Test
    fun stringFunctions() {
        val out =
            run(
                """
                printh(sub("hello", 2, 3))
                printh(sub("hello", -3))
                printh(#split("1,2,3"))
                printh(split("1,2,3")[2] + 1)
                printh(chr(104, 105))
                printh(ord("a"))
                printh(tostr(255, true))
                printh(tonum("0x1f"))
                printh(tostr(tonum("x")))
                printh("n" .. 1.5)
                """.trimIndent(),
            )
        assertEquals(listOf("el", "llo", "3", "3", "hi", "97", "0x00ff.0000", "31", "[nil]", "n1.5"), out)
    }

    @Test
    fun randomIsSeeded() {
        val a = run("srand(42) printh(rnd(100)) printh(flr(rnd(10)))")
        val b = run("srand(42) printh(rnd(100)) printh(flr(rnd(10)))")
        assertEquals(a, b)
        val r = run("for i=1,100 do local v=rnd(5) if (v<0 or v>=5) printh('bad') end printh('ok')")
        assertEquals(listOf("ok"), r)
    }

    @Test
    fun frameLoopRunsInitUpdateDraw() {
        val out =
            run(
                """
                function _init() printh("init") n = 0 end
                function _update() n += 1 end
                function _draw() printh("draw " .. n) end
                """.trimIndent(),
                frames = 3,
            )
        assertEquals(listOf("init", "draw 1", "draw 2", "draw 3"), out)
    }

    @Test
    fun flipLoopRunsOneIterationPerFrame() {
        val out =
            run(
                """
                local i = 0
                ::top::
                i += 1
                printh(i)
                flip()
                goto top
                """.trimIndent(),
                frames = 3,
            )
        assertEquals(listOf("1", "2", "3"), out)
    }

    @Test
    fun runtimeErrorsNameTheLineAndVariable() {
        val out = run("x = 1\nfoo()")
        assertEquals(listOf("ERROR: runtime error line 2: attempt to call a nil value (global 'foo')"), out)
    }

    @Test
    fun syntaxErrorsAreReported() {
        val out = run("x = = 1")
        assertTrue(out.single().startsWith("ERROR: syntax error line 1"), out.toString())
    }

    @Test
    fun cartDataPersists() {
        val t = TestConsole("cartdata('test') dset(3, 42) printh(dget(3))").frames(1)
        assertEquals(listOf("42"), t.log)
        assertEquals(42 shl 16, t.saved["test"]!![3])
    }

    @Test
    fun timeAdvancesPerFrame() {
        val out =
            run(
                "function _update() end function _draw() printh(t()) end",
                frames = 2,
            )
        assertEquals(listOf("0", "0.0333"), out)
    }
}
