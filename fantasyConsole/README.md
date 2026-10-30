# fantasyConsole

A headless Kotlin Multiplatform runtime for 128x128, 16-color fantasy-console cartridges
(`.p8`, `.p8.png`, `.p8.rom`): the Lua dialect with 16:16 fixed-point numbers, memory, graphics,
sound, input and cart loading. Stdlib only, so it can become its own library. Design and status:
[`plans/2026-10-30-fantasy-console-runtime.md`](plans/2026-10-30-fantasy-console-runtime.md).

```kotlin
val console = FantasyConsole(CartLoader.load(bytes), host) // host: printh, cart data, other carts
// On one thread with a FantasyConsole.RECOMMENDED_STACK_BYTES stack, every 1/console.fps s:
console.setButtons(0, mask)            // bits: left, right, up, down, O, X
console.step()
console.setPointer(x, y, buttons)      // optional: mouse and keyboard, for carts that ask
console.type(text)
console.render(pixels)                 // IntArray(128 * 128), ARGB, with the pause menu
play(console.audioBuffer, console.audioSamples) // 22,050 Hz mono floats
// console.state / console.error tell when the cart stops; console.abort() works from any thread.
```

Run real carts headless and get screenshots (carts are not checked in):

```bash
./gradlew :fantasyConsole:jvmTest --tests '*CartSmokeTest*' \
  -DfantasyConsole.carts=/path/to/carts -DfantasyConsole.out=/tmp/screens
```
