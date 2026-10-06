package com.pixelwhisper

import android.content.Context
import java.io.File

/** Model files pushed by scripts/setup-phone.sh. Exact sizes guard native loading: a bad file is a native crash. */
object Models {
    const val ENCODER = "turbo-encoder.int8.onnx"
    const val DECODER = "turbo-decoder.int8.onnx"
    const val TOKENS = "turbo-tokens.txt"
    const val VAD = "silero_vad.onnx"

    private val sizes = mapOf(
        ENCODER to 674_716_297L,
        DECODER to 361_080_764L,
        TOKENS to 816_730L,
        VAD to 643_854L,
    )

    fun path(context: Context, name: String): String =
        File(context.getExternalFilesDir(null), "models/$name").path

    /** Files that are absent or do not have the expected size. */
    fun missing(context: Context): List<String> =
        sizes.filter { (name, size) -> File(path(context, name)).length() != size }.keys.toList()
}
