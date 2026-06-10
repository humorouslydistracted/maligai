package com.maligai.app

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A single captured point with a timestamp (ms). */
data class TimedPoint(val x: Float, val y: Float, val t: Long)

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
    addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * Wraps ML Kit Digital Ink Recognition for one regional script (from settings)
 * plus required English ("en").
 *
 * Models download once (~15–20 MB each) and work fully offline afterwards.
 */
@Singleton
class Recognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val remoteModelManager = RemoteModelManager.getInstance()

    @Volatile private var primaryTag: String = ScriptLanguages.DEFAULT_TAG
    @Volatile private var modelRegional: DigitalInkRecognitionModel? = buildModel(primaryTag)
    private val modelEn: DigitalInkRecognitionModel? = buildModel(ScriptLanguages.EN_TAG)

    @Volatile internal var recognizerRegional: DigitalInkRecognizer? = null
    @Volatile internal var recognizerEn: DigitalInkRecognizer? = null

    val isSupported: Boolean get() = modelRegional != null

    // ------------------------------------------------------------------ config

    /** Switches the active regional model tag; closes any in-memory regional recognizer. */
    fun setPrimaryScriptTag(tag: String) {
        if (tag == primaryTag && modelRegional != null) return
        recognizerRegional?.close()
        recognizerRegional = null
        primaryTag = tag
        modelRegional = buildModel(tag)
    }

    fun currentPrimaryTag(): String = primaryTag

    // ------------------------------------------------------------------ check

    suspend fun isRegionalModelDownloaded(tag: String = primaryTag): Boolean =
        checkDownloaded(buildModel(tag))

    /** @deprecated use [isRegionalModelDownloaded] */
    suspend fun isModelDownloaded(): Boolean = isRegionalModelDownloaded()

    suspend fun isEnModelDownloaded(): Boolean = checkDownloaded(modelEn)

    private suspend fun checkDownloaded(model: DigitalInkRecognitionModel?): Boolean {
        model ?: return false
        return try { remoteModelManager.isModelDownloaded(model).await() } catch (_: Exception) { false }
    }

    // ---------------------------------------------------------------- download

    suspend fun downloadRegionalModel(tag: String = primaryTag, requireWifi: Boolean = false): Boolean {
        if (tag != primaryTag) setPrimaryScriptTag(tag)
        return doDownload(modelRegional, requireWifi) { recognizerRegional = it }
    }

    /** @deprecated use [downloadRegionalModel] */
    suspend fun downloadModel(requireWifi: Boolean = false): Boolean = downloadRegionalModel(requireWifi = requireWifi)

    suspend fun downloadEnModel(requireWifi: Boolean = false): Boolean =
        doDownload(modelEn, requireWifi) { recognizerEn = it }

    private suspend fun doDownload(
        model: DigitalInkRecognitionModel?,
        requireWifi: Boolean,
        onBuilt: (DigitalInkRecognizer) -> Unit
    ): Boolean {
        model ?: return false
        return try {
            val conditions = DownloadConditions.Builder()
                .apply { if (requireWifi) requireWifi() }
                .build()
            remoteModelManager.download(model, conditions).await()
            val rec = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build()
            )
            onBuilt(rec)
            true
        } catch (_: Exception) { false }
    }

    // ----------------------------------------------------------- initialise

    /**
     * Lazily initialises recognizers for any already-downloaded models.
     * Returns true when both regional and English recognizers are ready.
     */
    suspend fun ensureReady(): Boolean {
        val regionalModel = modelRegional
        if (recognizerRegional == null && checkDownloaded(regionalModel)) {
            regionalModel?.let { m ->
                recognizerRegional = DigitalInkRecognition.getClient(
                    DigitalInkRecognizerOptions.builder(m).build()
                )
            }
        }
        if (recognizerEn == null && checkDownloaded(modelEn)) {
            modelEn?.let { m ->
                recognizerEn = DigitalInkRecognition.getClient(
                    DigitalInkRecognizerOptions.builder(m).build()
                )
            }
        }
        return recognizerRegional != null && recognizerEn != null
    }

    // ------------------------------------------------------------- recognise

    /**
     * Recognises strokes and returns merged, deduplicated candidates
     * (regional model first, then English).
     */
    suspend fun recognize(strokes: List<List<TimedPoint>>): List<String> {
        if (strokes.isEmpty() || strokes.all { it.isEmpty() }) return emptyList()
        if (!ensureReady()) return emptyList()

        val ink = buildInk(strokes) ?: return emptyList()
        val seen = LinkedHashSet<String>()

        recognizerRegional?.runCatching {
            recognize(ink).await().candidates
                .map { it.text }
                .filter { it.isNotBlank() }
                .forEach { seen.add(it) }
        }

        recognizerEn?.runCatching {
            recognize(ink).await().candidates
                .map { it.text }
                .filter { it.isNotBlank() }
                .forEach { seen.add(it) }
        }

        return seen.toList()
    }

    suspend fun recognizeAll(strokes: List<List<TimedPoint>>): List<String> = recognize(strokes)

    // ---------------------------------------------------------------- helpers

    private fun buildInk(strokes: List<List<TimedPoint>>): Ink? {
        val inkBuilder = Ink.builder()
        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            val sb = Ink.Stroke.builder()
            for (p in stroke) sb.addPoint(Ink.Point.create(p.x, p.y, p.t))
            inkBuilder.addStroke(sb.build())
        }
        val ink = inkBuilder.build()
        return if (ink.strokes.isEmpty()) null else ink
    }

    private fun buildModel(tag: String): DigitalInkRecognitionModel? =
        DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)
            ?.let { DigitalInkRecognitionModel.builder(it).build() }

    fun release() {
        recognizerRegional?.close(); recognizerRegional = null
        recognizerEn?.close(); recognizerEn = null
    }
}
