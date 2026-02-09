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
package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "resourceType",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Practitioner::class, name = "Practitioner"),
    JsonSubTypes.Type(value = Patient::class, name = "Patient"),
    JsonSubTypes.Type(value = Bundle::class, name = "Bundle"),
    JsonSubTypes.Type(value = VisionPrescription::class, name = "VisionPrescription"),
)
@Stable
open class Resource(
    var resourceType: String? = null,
    var id: String = "",
)

@Stable
class Practitioner(
    resourceType: String? = null,
    id: String = "",
    var active: Boolean? = null,
    var name: ArrayList<HumanName> = arrayListOf(),
    var gender: String? = null,
) : Resource(resourceType, id)

@Stable
class Patient(
    resourceType: String? = null,
    id: String = "",
    var active: Boolean? = null,
    var name: ArrayList<HumanName> = arrayListOf(),
    var gender: String? = null,
) : Resource(resourceType, id)

@Stable
class HumanName(
    var use: String? = null,
    var family: String? = null,
    var given: ArrayList<String> = arrayListOf(),
) {
    fun assembleName(): String = given.joinToString(" ") + " " + family
}

@Stable
class Bundle(
    resourceType: String? = null,
    id: String = "",
    var type: String? = null,
    var created: String? = null,
    var entry: List<Resource> = arrayListOf(),
) : Resource(resourceType, id)

@Stable
class VisionPrescription(
    resourceType: String? = null,
    id: String = "",
    var status: String? = null,
    var created: String? = null,
    var patient: Reference? = Reference(),
    var encounter: Reference? = Reference(),
    var dateWritten: String? = null,
    var prescriber: Reference? = Reference(),
    var lensSpecification: List<LensSpecification> = arrayListOf(),
) : Resource(resourceType, id) {
    fun glasses() = lensSpecification.filter { it.product == "lens" }

    fun contacts() = lensSpecification.filter { it.product == "contacts" }

    fun glassesRightEyes() = lensSpecification.filter { it.product == "lens" && it.eye == "right" }

    fun glassesLeftEyes() = lensSpecification.filter { it.product == "lens" && it.eye == "left" }

    fun contactsRightEyes() = lensSpecification.filter { it.product == "contacts" && it.eye == "right" }

    fun contactsLeftEyes() = lensSpecification.filter { it.product == "contacts" && it.eye == "left" }
}

@Stable
class LensSpecification(
    var product: String? = null,
    var eye: String? = null,
    var sphere: Double? = null,
    var cylinder: Double? = null,
    var axis: Double? = null,
    var pd: Double? = null,
    var interAdd: Double? = null,
    var add: Double? = null,
    var prism: Prism? = null,
    // contact lenses
    var power: Double? = null,
    var backCurve: Double? = null,
    var diameter: Double? = null,
    var color: String? = null,
    var brand: String? = null,
    var note: String? = null,
)

@Stable
class Prism(
    var amount: Double? = null,
    var base: String? = null,
)

class Reference(
    var reference: String? = null,
)

data class FhirElementDatabase(
    var baseResource: Resource? = null,
    var localDb: ImmutableMap<String, Resource> = persistentMapOf(),
)

fun findReferenceInDb(
    it: String,
    db: Map<String, Resource>,
): Resource? {
    val parts = it.split("/")
    return if (parts.size == 2) {
        db.get(parts[1].removePrefix("#"))
    } else {
        db.get(it.removePrefix("#"))
    }
}

fun parseResourceBundleOrNull(json: String): FhirElementDatabase? {
    val mapper =
        jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    return try {
        val resource = mapper.readValue<Resource>(json)

        val db =
            when (resource) {
                is Bundle -> {
                    resource.entry.associateBy { it.id }.toImmutableMap()
                }
                else -> {
                    persistentMapOf(resource.id to resource)
                }
            }

        FhirElementDatabase(
            localDb = db,
            baseResource = resource,
        )
    } catch (e: Exception) {
        Log.e("RenderEyeGlassesPrescription", "Parser error", e)
        null
    }
}
