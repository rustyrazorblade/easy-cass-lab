package com.rustyrazorblade.easydblab.providers.aws

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Serializer for IamPolicyAwsPrincipal that outputs either a string or array of strings.
 */
object IamPolicyAwsPrincipalSerializer : KSerializer<IamPolicyAwsPrincipal> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("IamPolicyAwsPrincipal")

    override fun serialize(
        encoder: Encoder,
        value: IamPolicyAwsPrincipal,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is IamPolicyAwsPrincipal.Single -> jsonEncoder.encodeJsonElement(JsonPrimitive(value.value))
            is IamPolicyAwsPrincipal.Multiple ->
                jsonEncoder.encodeJsonElement(
                    JsonArray(value.values.map { JsonPrimitive(it) }),
                )
        }
    }

    override fun deserialize(decoder: Decoder): IamPolicyAwsPrincipal {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> IamPolicyAwsPrincipal.Single(element.content)
            is JsonArray -> IamPolicyAwsPrincipal.Multiple(element.map { it.jsonPrimitive.content })
            else -> error("Unexpected JSON element type for AWS principal: $element")
        }
    }
}

/**
 * Serializer for IamPolicyAction that outputs either a string or array of strings.
 */
object IamPolicyActionSerializer : KSerializer<IamPolicyAction> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("IamPolicyAction", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: IamPolicyAction,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is IamPolicyAction.Single -> jsonEncoder.encodeJsonElement(JsonPrimitive(value.value))
            is IamPolicyAction.Multiple ->
                jsonEncoder.encodeJsonElement(
                    JsonArray(value.values.map { JsonPrimitive(it) }),
                )
        }
    }

    override fun deserialize(decoder: Decoder): IamPolicyAction {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> IamPolicyAction.Single(element.content)
            is JsonArray -> IamPolicyAction.Multiple(element.map { it.jsonPrimitive.content })
            else -> error("Unexpected JSON element type for action: $element")
        }
    }
}

/**
 * Serializer for IamPolicyResource that outputs either a string or array of strings.
 */
object IamPolicyResourceSerializer : KSerializer<IamPolicyResource> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("IamPolicyResource", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: IamPolicyResource,
    ) {
        val jsonEncoder = encoder as JsonEncoder
        when (value) {
            is IamPolicyResource.Single -> jsonEncoder.encodeJsonElement(JsonPrimitive(value.value))
            is IamPolicyResource.Multiple ->
                jsonEncoder.encodeJsonElement(
                    JsonArray(value.values.map { JsonPrimitive(it) }),
                )
        }
    }

    override fun deserialize(decoder: Decoder): IamPolicyResource {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> IamPolicyResource.Single(element.content)
            is JsonArray -> IamPolicyResource.Multiple(element.map { it.jsonPrimitive.content })
            else -> error("Unexpected JSON element type for resource: $element")
        }
    }
}
