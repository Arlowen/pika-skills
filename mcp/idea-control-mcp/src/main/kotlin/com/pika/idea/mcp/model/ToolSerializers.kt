package com.pika.idea.mcp.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

// IDEA 2024.2 repackages kotlinx.serialization without the metadata required by the compiler
// plugin. Explicit serializers keep our argument schemas compatible with MCP Server 1.0.30.
object StartServiceArgsSerializer : KSerializer<StartServiceArgs> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.pika.idea.mcp.model.StartServiceArgs") {
            element<String>("configName")
            element<String>("mode", isOptional = true)
            element<Boolean>("allowMultiple", isOptional = true)
            element<Int>("startTimeoutSeconds", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): StartServiceArgs {
        var configName: String? = null
        var mode = "DEBUG"
        var allowMultiple = false
        var startTimeoutSeconds = 30
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> configName = decodeStringElement(descriptor, 0)
                    1 -> mode = decodeStringElement(descriptor, 1)
                    2 -> allowMultiple = decodeBooleanElement(descriptor, 2)
                    3 -> startTimeoutSeconds = decodeIntElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected argument index $index")
                }
            }
        }
        return StartServiceArgs(
            configName = requireNotNull(configName) { "configName is required" },
            mode = mode,
            allowMultiple = allowMultiple,
            startTimeoutSeconds = startTimeoutSeconds,
        )
    }

    override fun serialize(encoder: Encoder, value: StartServiceArgs) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.configName)
            encodeStringElement(descriptor, 1, value.mode)
            encodeBooleanElement(descriptor, 2, value.allowMultiple)
            encodeIntElement(descriptor, 3, value.startTimeoutSeconds)
        }
    }
}

object StopServiceArgsSerializer : KSerializer<StopServiceArgs> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.pika.idea.mcp.model.StopServiceArgs") {
            element<Long>("executionId")
            element<Boolean>("waitForTermination", isOptional = true)
            element<Int>("stopTimeoutSeconds", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): StopServiceArgs {
        var executionId: Long? = null
        var waitForTermination = true
        var stopTimeoutSeconds = 30
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> executionId = decodeLongElement(descriptor, 0)
                    1 -> waitForTermination = decodeBooleanElement(descriptor, 1)
                    2 -> stopTimeoutSeconds = decodeIntElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected argument index $index")
                }
            }
        }
        return StopServiceArgs(
            executionId = requireNotNull(executionId) { "executionId is required" },
            waitForTermination = waitForTermination,
            stopTimeoutSeconds = stopTimeoutSeconds,
        )
    }

    override fun serialize(encoder: Encoder, value: StopServiceArgs) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.executionId)
            encodeBooleanElement(descriptor, 1, value.waitForTermination)
            encodeIntElement(descriptor, 2, value.stopTimeoutSeconds)
        }
    }
}

object MoveChangesArgsSerializer : KSerializer<MoveChangesArgs> {
    private val pathsSerializer = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.pika.idea.mcp.model.MoveChangesArgs") {
            element<String>("changelistName")
            element("paths", pathsSerializer.descriptor)
            element<Boolean>("createIfMissing", isOptional = true)
            element<Boolean>("allOrNothing", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): MoveChangesArgs {
        var changelistName: String? = null
        var paths: List<String>? = null
        var createIfMissing = true
        var allOrNothing = true
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> changelistName = decodeStringElement(descriptor, 0)
                    1 -> paths = decodeSerializableElement(descriptor, 1, pathsSerializer)
                    2 -> createIfMissing = decodeBooleanElement(descriptor, 2)
                    3 -> allOrNothing = decodeBooleanElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected argument index $index")
                }
            }
        }
        return MoveChangesArgs(
            changelistName = requireNotNull(changelistName) { "changelistName is required" },
            paths = requireNotNull(paths) { "paths is required" },
            createIfMissing = createIfMissing,
            allOrNothing = allOrNothing,
        )
    }

    override fun serialize(encoder: Encoder, value: MoveChangesArgs) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.changelistName)
            encodeSerializableElement(descriptor, 1, pathsSerializer, value.paths)
            encodeBooleanElement(descriptor, 2, value.createIfMissing)
            encodeBooleanElement(descriptor, 3, value.allOrNothing)
        }
    }
}
