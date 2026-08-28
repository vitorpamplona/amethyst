import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

// ============================================================================
// Android resource table for the JVM target
// ============================================================================
// On Android, `com.vitorpamplona.amethyst.R` and the string/plural lookup come
// from aapt2 + the framework. The JVM has neither, so we generate the same
// `R` class and a runtime lookup table from the very same `res/values*/`
// tree that aapt2 consumes.
//
// The point is that shared code keeps writing `stringRes(R.string.foo)` — all
// ~3900 existing call sites compile unchanged on both targets. Resource ids
// never cross the Android/JVM boundary, so the two `R` classes are free to
// number their entries differently.
//
// Generation rules mirror aapt2:
//   - `"…"`-quoted values keep their whitespace verbatim; unquoted values have
//     whitespace runs collapsed to a single space and are then trimmed.
//   - Backslash escapes (\n \t \' \" \@ \? \\ \uXXXX) are resolved here, as
//     aapt2 resolves them at compile time.
//   - `translatable="false"` is irrelevant to us: we read whatever each
//     locale's file actually declares.
val resSourceDir: Directory = layout.projectDirectory.dir("src/androidMain/res")
val generatedResRoot: Provider<Directory> = layout.buildDirectory.dir("generated/androidRes")

val generateAndroidResourceTable by tasks.registering(GenerateAndroidResourceTable::class) {
    group = "build"
    description = "Generate the JVM R class and locale string/plural tables from the Android res/ tree."
    resDir.set(resSourceDir)
    kotlinOutputDir.set(generatedResRoot.map { it.dir("kotlin") })
    resourceOutputDir.set(generatedResRoot.map { it.dir("resources") })
}

@CacheableTask
abstract class GenerateAndroidResourceTable : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resDir: DirectoryProperty

    @get:OutputDirectory
    abstract val kotlinOutputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val resourceOutputDir: DirectoryProperty

    /** aapt2's id layout: 0x7f<type><entry>. The package byte is cosmetic here. */
    private fun resourceId(typeId: Int, index: Int) = 0x7f000000 or (typeId shl 16) or index

    @TaskAction
    fun generate() {
        val res = resDir.get().asFile
        val valueDirs =
            res
                .listFiles { f -> f.isDirectory && (f.name == "values" || f.name.startsWith("values-")) }
                ?.sortedBy { it.name }
                .orEmpty()

        // Qualifier "" is the default (values/); everything else keeps aapt's
        // folder qualifier verbatim (de, de-rDE, zh-rHans, …) and is matched
        // against the running locale at runtime.
        val perQualifierStrings = LinkedHashMap<String, MutableMap<String, String>>()
        val perQualifierPlurals = LinkedHashMap<String, MutableMap<String, MutableMap<String, String>>>()

        valueDirs.forEach { dir ->
            val stringsFile = File(dir, "strings.xml")
            if (!stringsFile.isFile) return@forEach
            val qualifier = if (dir.name == "values") "" else dir.name.removePrefix("values-")
            val parsed = parseStringsXml(stringsFile)
            if (parsed.strings.isNotEmpty()) {
                perQualifierStrings.getOrPut(qualifier) { linkedMapOf() }.putAll(parsed.strings)
            }
            if (parsed.plurals.isNotEmpty()) {
                perQualifierPlurals.getOrPut(qualifier) { linkedMapOf() }.putAll(parsed.plurals)
            }
        }

        // The default locale defines the id space: a name that exists only in a
        // translation has no `R` field on Android either, so it cannot be
        // referenced from code and is dropped here too.
        val stringNames = perQualifierStrings[""]?.keys?.sorted().orEmpty()
        val pluralNames = perQualifierPlurals[""]?.keys?.sorted().orEmpty()
        val drawableNames = collectDrawableNames(res)

        val stringIds = stringNames.withIndex().associate { (i, n) -> n to resourceId(TYPE_STRING, i) }
        val pluralIds = pluralNames.withIndex().associate { (i, n) -> n to resourceId(TYPE_PLURALS, i) }
        val drawableIds = drawableNames.withIndex().associate { (i, n) -> n to resourceId(TYPE_DRAWABLE, i) }

        writeRClass(stringIds, pluralIds, drawableIds)
        writeTables(perQualifierStrings, perQualifierPlurals, stringIds, pluralIds)
        copyDrawables(res, drawableIds)

        logger.lifecycle(
            "generateAndroidResourceTable: ${stringNames.size} strings, ${pluralNames.size} plurals, " +
                "${drawableNames.size} drawables across ${perQualifierStrings.size} locale qualifiers",
        )
    }

    private fun writeRClass(
        stringIds: Map<String, Int>,
        pluralIds: Map<String, Int>,
        drawableIds: Map<String, Int>,
    ) {
        val out = File(kotlinOutputDir.get().asFile, "com/vitorpamplona/amethyst/shared/R.kt")
        out.parentFile.mkdirs()
        val sb = StringBuilder()
        sb.append("// Generated by :amethystShared:generateAndroidResourceTable. DO NOT EDIT.\n")
        sb.append("@file:Suppress(\"ClassName\", \"ObjectPropertyName\", \"unused\")\n\n")
        sb.append("package com.vitorpamplona.amethyst.shared\n\n")
        sb.append("/** JVM stand-in for the aapt2-generated `R`. See :androidStubs/README.md. */\n")
        sb.append("public object R {\n")
        listOf("string" to stringIds, "plurals" to pluralIds, "drawable" to drawableIds).forEach { (type, ids) ->
            sb.append("    public object $type {\n")
            ids.forEach { (name, id) -> sb.append("        public const val $name: Int = $id\n") }
            sb.append("    }\n")
        }
        sb.append("}\n")
        out.writeText(sb.toString())
    }

    private fun writeTables(
        strings: Map<String, Map<String, String>>,
        plurals: Map<String, Map<String, Map<String, String>>>,
        stringIds: Map<String, Int>,
        pluralIds: Map<String, Int>,
    ) {
        val root = File(resourceOutputDir.get().asFile, TABLE_DIR)
        root.deleteRecursively()
        root.mkdirs()

        val qualifiers = (strings.keys + plurals.keys).toSortedSet()
        qualifiers.forEach { qualifier ->
            val slug = if (qualifier.isEmpty()) "default" else qualifier
            val sb = StringBuilder()
            strings[qualifier]?.forEach { (name, value) ->
                val id = stringIds[name] ?: return@forEach
                sb.append("s\t").append(id).append('\t').append(encode(value)).append('\n')
            }
            plurals[qualifier]?.forEach { (name, byQuantity) ->
                val id = pluralIds[name] ?: return@forEach
                byQuantity.forEach { (quantity, value) ->
                    sb.append("p\t").append(id).append('\t').append(quantity).append('\t').append(encode(value)).append('\n')
                }
            }
            File(root, "$slug.tsv").writeText(sb.toString())
        }
        File(root, "qualifiers.txt").writeText(
            qualifiers.joinToString("\n") { if (it.isEmpty()) "default" else it } + "\n",
        )
    }

    /** Tab and newline are the record separators, so they travel escaped. */
    private fun encode(value: String) =
        value
            .replace("\\", "\\\\")
            .replace("\t", "\\t")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    private fun collectDrawableNames(res: File): List<String> =
        drawableDirs(res)
            .flatMap { dir -> dir.listFiles()?.toList().orEmpty() }
            .filter { it.isFile }
            .map { it.name.substringBefore('.') }
            .distinct()
            .sorted()

    private fun drawableDirs(res: File): List<File> =
        res
            .listFiles { f -> f.isDirectory && (f.name == "drawable" || f.name.startsWith("drawable-")) }
            .orEmpty()
            .toList()

    /**
     * Density buckets exist because Android picks one at install time; the JVM
     * has a single artifact and scales at draw time, so take the largest raster
     * available and let Skia downscale. Vectors are density-independent already.
     *
     * `-night` is kept as a separate variant so the JVM can honour dark theme
     * the way Android's resource qualifier does.
     */
    private val densityPreference =
        listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi", "ldpi", "nodpi", "anydpi", "tvdpi")

    private fun copyDrawables(
        res: File,
        drawableIds: Map<String, Int>,
    ) {
        val outRoot = File(resourceOutputDir.get().asFile, "$TABLE_DIR/drawable")
        outRoot.mkdirs()
        val index = StringBuilder()

        drawableIds.forEach { (name, id) ->
            listOf(false, true).forEach { night ->
                val source = pickDrawableFile(res, name, night) ?: return@forEach
                val variant = if (night) "night" else "default"
                val fileName = "$variant-$name.${source.extension}"
                source.copyTo(File(outRoot, fileName), overwrite = true)
                index.append(id).append('\t').append(variant).append('\t').append(fileName).append('\n')
            }
        }
        File(resourceOutputDir.get().asFile, "$TABLE_DIR/drawables.tsv").writeText(index.toString())
    }

    private fun pickDrawableFile(
        res: File,
        name: String,
        night: Boolean,
    ): File? {
        val dirs = drawableDirs(res)
        fun qualifiers(dir: File) = dir.name.removePrefix("drawable").removePrefix("-").split('-').filter { it.isNotEmpty() }
        val candidates =
            dirs.filter { dir ->
                val q = qualifiers(dir)
                if (night) "night" in q else "night" !in q
            }
        // Vectors first (resolution independent), then the densest raster.
        val ordered =
            candidates.sortedWith(
                compareBy(
                    { if (qualifiers(it).isEmpty()) 0 else 1 },
                    {
                        val idx = qualifiers(it).firstNotNullOfOrNull { q -> densityPreference.indexOf(q).takeIf { i -> i >= 0 } }
                        idx ?: densityPreference.size
                    },
                ),
            )
        val xml = ordered.firstNotNullOfOrNull { dir -> File(dir, "$name.xml").takeIf { it.isFile } }
        if (xml != null) return xml
        return ordered.firstNotNullOfOrNull { dir ->
            dir.listFiles { f -> f.isFile && f.name.substringBefore('.') == name }?.firstOrNull()
        }
    }

    private class ParsedStrings(
        val strings: Map<String, String>,
        val plurals: Map<String, MutableMap<String, String>>,
    )

    private fun parseStringsXml(file: File): ParsedStrings {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        // Local files only, but keep the parser from reaching the network or
        // the filesystem through a crafted DTD regardless.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        val doc = factory.newDocumentBuilder().parse(file)

        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, MutableMap<String, String>>()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val el = stringNodes.item(i) as org.w3c.dom.Element
            val name = el.getAttribute("name")
            if (name.isNullOrEmpty()) continue
            strings[name] = unescapeAapt(el.textContent ?: "")
        }

        val pluralNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val el = pluralNodes.item(i) as org.w3c.dom.Element
            val name = el.getAttribute("name")
            if (name.isNullOrEmpty()) continue
            val items = linkedMapOf<String, String>()
            val itemNodes = el.getElementsByTagName("item")
            for (j in 0 until itemNodes.length) {
                val item = itemNodes.item(j) as org.w3c.dom.Element
                val quantity = item.getAttribute("quantity")
                if (quantity.isNullOrEmpty()) continue
                items[quantity] = unescapeAapt(item.textContent ?: "")
            }
            if (items.isNotEmpty()) plurals[name] = items
        }
        return ParsedStrings(strings, plurals)
    }

    /**
     * Resolve the escaping aapt2 applies to a resource value: backslash
     * escapes, `"…"` whitespace preservation, and whitespace collapsing +
     * trimming outside quotes.
     */
    internal fun unescapeAapt(raw: String): String {
        val out = StringBuilder(raw.length)
        var inQuotes = false
        var pendingSpace = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' && i + 1 < raw.length -> {
                    if (pendingSpace) { out.append(' '); pendingSpace = false }
                    val next = raw[i + 1]
                    i += 2
                    when (next) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'u' -> {
                            val hex = raw.substring(i, minOf(i + 4, raw.length))
                            val code = hex.toIntOrNull(16)
                            if (code != null && hex.length == 4) {
                                out.append(code.toChar())
                                i += 4
                            } else {
                                out.append("\\u")
                            }
                        }
                        else -> out.append(next)
                    }
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    i++
                }
                c.isWhitespace() && !inQuotes -> {
                    // Collapse a run of whitespace; emit it only once we know a
                    // non-space follows, which also drops trailing whitespace.
                    if (out.isNotEmpty()) pendingSpace = true
                    i++
                }
                else -> {
                    if (pendingSpace) { out.append(' '); pendingSpace = false }
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    companion object {
        const val TABLE_DIR = "amethyst-res"
        private const val TYPE_STRING = 0x04
        private const val TYPE_PLURALS = 0x05
        private const val TYPE_DRAWABLE = 0x06
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    android {
        namespace = "com.vitorpamplona.amethyst.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }

        // The app's strings and drawables now live here, so aapt2 generates
        // `com.vitorpamplona.amethyst.shared.R` for the Android target and the
        // generator above produces the matching class for the JVM target.
        androidResources.enable = true
    }

    sourceSets {
        // Compiled into BOTH the Android and the JVM target. Code here may
        // reference `android.*` types: they resolve from android.jar on Android
        // and from :androidStubs on the JVM. See :androidStubs/README.md.
        commonMain {
            dependencies {
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.androidx.collection)
            }
        }

        val jvmAndroid =
            create("jvmAndroid") {
                dependsOn(commonMain.get())
                dependencies {
                    compileOnly(project(":androidStubs"))
                }
            }

        androidMain {
            dependsOn(jvmAndroid)
            dependencies {
                implementation(libs.androidx.ui)
                // stringResource / painterResource actuals read Android resources;
                // LifecycleResumeEffect drives the locale-change cache eviction.
                implementation(libs.androidx.lifecycle.runtime.compose)
            }
        }

        jvmMain {
            dependsOn(jvmAndroid)
            kotlin.srcDir(generateAndroidResourceTable.map { it.kotlinOutputDir })
            resources.srcDir(generateAndroidResourceTable.map { it.resourceOutputDir })
            dependencies {
                implementation(project(":androidStubs"))
                // CLDR plural rules + locale matching. Unicode-3.0 (permissive).
                // Android selects plurals with its bundled `android.icu`, so
                // using ICU here keeps the two platforms behaviourally identical.
                implementation(libs.icu4j)
                implementation(compose.desktop.currentOs)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
