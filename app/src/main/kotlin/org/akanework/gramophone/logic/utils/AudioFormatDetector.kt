package org.akanework.gramophone.logic.utils

import android.os.Parcelable
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import kotlinx.parcelize.Parcelize
import java.io.File

class AudioFormatDetector {
    companion object {
        @Parcelize
        enum class AudioQuality : Parcelable {
            UNKNOWN,    // Unable to determine quality
            LOSSY,      // Compressed formats (MP3, AAC, OGG)
            CD,         // 16-bit/44.1kHz or 16-bit/48kHz (Red Book)
            HD,         // 24-bit/44.1kHz or 24-bit/48kHz
            HIRES       // 24-bit/88.2kHz+ (Hi-Res Audio standard)
        }

        @Parcelize
        enum class SpatialFormat : Parcelable {
            NONE,           // Mono
            STEREO,         // 2.0: Left, Right
            QUAD,           // 4.0: FL, FR, BL, BR (Quadraphonic)
            SURROUND_5_0,   // 5.0: FL, FR, FC, BL, BR
            SURROUND_5_1,   // 5.1: FL, FR, FC, LFE, BL, BR
            SURROUND_6_1,   // 6.1: FL, FR, FC, LFE, BC, SL, SR
            SURROUND_7_1,   // 7.1: FL, FR, FC, LFE, BL, BR, SL, SR
            DOLBY_ATMOS,    // Dolby Atmos (object-based)
            DOLBY_AC4,      // Dolby AC-4
            DOLBY_AC3,      // Dolby Digital
            DOLBY_EAC3,     // Dolby Digital Plus
            DOLBY_EAC3_JOC, // Dolby Digital Plus with Joint Object Coding
        }

        @Parcelize
        data class AudioFormatInfo(
            val quality: AudioQuality,
            val sampleRate: Int,
            val bitDepth: Int,
            val isLossless: Boolean,
            val channelCount: Int,
            val bitrate: Int?,
            val mimeType: String?,
            val spatialFormat: SpatialFormat,
            val encoderPadding: Int?,
            val encoderDelay: Int?
        ) : Parcelable {
            override fun toString(): String {
                return """
                    Audio Format Details:
                    Quality Tier: $quality
                    Sample Rate: $sampleRate Hz
                    Bit Depth: $bitDepth bit
                    Channel Count: $channelCount
                    Lossless: $isLossless
                    Spatial Format: $spatialFormat
                    Codec: $mimeType
                    ${bitrate?.let { "Bitrate: ${it / 1000} kbps" } ?: ""}
                """.trimIndent()
            }
        }

        @UnstableApi
        fun detectAudioFormat(tracks: Tracks, player: Player?): AudioFormatInfo? {
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        if (!group.isTrackSelected(i)) continue

                        val format = group.getTrackFormat(i)
                        val bitrate = calculateBitrate(player)

                        val rawSampleRate = format.sampleRate
                        val sampleRate = normalizeToStandardRate(rawSampleRate)
                        val bitDepth = detectBitDepth(format)
                        val isLossless = isLosslessFormat(format.sampleMimeType)
                        val spatialFormat = detectSpatialFormat(format)
                        val channelCount = format.channelCount

                        val quality = determineQualityTier(
                            sampleRate = sampleRate,
                            bitDepth = bitDepth,
                            isLossless = isLossless
                        )

                        return AudioFormatInfo(
                            quality = quality,
                            sampleRate = sampleRate,
                            bitDepth = bitDepth,
                            isLossless = isLossless,
                            channelCount = channelCount,
                            bitrate = bitrate,
                            mimeType = format.sampleMimeType,
                            spatialFormat = spatialFormat,
                            encoderPadding = format.encoderPadding.takeIf { it != Format.NO_VALUE },
                            encoderDelay = format.encoderDelay.takeIf { it != Format.NO_VALUE }
                        )
                    }
                }
            }
            return null
        }

        private fun calculateBitrate(player: Player?): Int? {
            if (player == null) return null

            val duration = player.duration
            if (duration <= 0) return null

            val mediaItem = player.currentMediaItem ?: return null
            val uri = mediaItem.localConfiguration?.uri ?: return null

            try {
                val file = File(uri.path!!)
                val sizeInBits = file.length() * 8
                return (sizeInBits / (duration / 1000.0)).toInt()
            } catch (e: Exception) {
                return null
            }
        }

        private fun normalizeToStandardRate(rate: Int): Int {
            return when (rate) {
                in 44000..44200 -> 44100    // CD standard
                in 47900..48100 -> 48000    // DVD standard
                in 87900..88300 -> 88200    // 2x 44.1
                in 95900..96100 -> 96000    // DVD-Audio/Hi-Res
                in 176200..176600 -> 176400 // 4x 44.1
                in 191900..192100 -> 192000 // Hi-Res
                else -> rate
            }
        }

        @UnstableApi
        private fun detectBitDepth(format: Format): Int {
            val pcmBitDepth = when (format.pcmEncoding) {
                C.ENCODING_PCM_16BIT -> 16
                C.ENCODING_PCM_24BIT -> 24
                C.ENCODING_PCM_32BIT -> 32
                C.ENCODING_PCM_FLOAT -> 32
                else -> null
            }

            if (pcmBitDepth != null) return pcmBitDepth

            return when (format.sampleMimeType) {
                MimeTypes.AUDIO_FLAC -> 16      // FLAC supports 4-24 bits
                MimeTypes.AUDIO_ALAC -> 16      // ALAC typically 16 or 24
                MimeTypes.AUDIO_WAV -> 16       // WAV typically 16-32
                MimeTypes.AUDIO_AAC -> 16       // AAC is perceptual codec
                MimeTypes.AUDIO_AC3,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_E_AC3_JOC -> 16 // Dolby Digital family
                MimeTypes.AUDIO_AC4 -> 24       // Dolby AC-4
                else -> 16                      // Conservative default
            }
        }

        private fun isLosslessFormat(mimeType: String?): Boolean = when (mimeType) {
            MimeTypes.AUDIO_FLAC,
            MimeTypes.AUDIO_ALAC,
            MimeTypes.AUDIO_WAV -> true

            else -> false
        }

        @UnstableApi
        private fun detectSpatialFormat(format: Format): SpatialFormat {
            val dolbyFormat = when (format.sampleMimeType) {
                MimeTypes.AUDIO_AC3 -> SpatialFormat.DOLBY_AC3
                MimeTypes.AUDIO_E_AC3 -> SpatialFormat.DOLBY_EAC3
                MimeTypes.AUDIO_E_AC3_JOC -> SpatialFormat.DOLBY_EAC3_JOC
                MimeTypes.AUDIO_AC4 -> SpatialFormat.DOLBY_AC4
                else -> null
            }

            if (dolbyFormat != null) return dolbyFormat

            format.metadata?.let { metadata ->
                for (i in 0 until metadata.length()) {
                    when {
                        metadata.get(i).toString().contains("Dolby Atmos") ->
                            return SpatialFormat.DOLBY_ATMOS
                    }
                }
            }

            // Standard multichannel formats
            return when (format.channelCount) {
                1 -> SpatialFormat.NONE          // Mono
                2 -> SpatialFormat.STEREO        // Standard stereo
                4 -> SpatialFormat.QUAD          // Quadraphonic
                5 -> SpatialFormat.SURROUND_5_0  // 5.0 surround
                6 -> SpatialFormat.SURROUND_5_1  // 5.1 surround
                7 -> SpatialFormat.SURROUND_6_1  // 6.1 surround
                8 -> SpatialFormat.SURROUND_7_1  // 7.1 surround
                else -> if (format.channelCount > 2) {
                    // Non-standard multichannel configuration
                    when {
                        format.channelCount > 6 -> SpatialFormat.SURROUND_7_1
                        format.channelCount > 5 -> SpatialFormat.SURROUND_5_1
                        format.channelCount > 4 -> SpatialFormat.SURROUND_5_0
                        else -> SpatialFormat.QUAD
                    }
                } else SpatialFormat.NONE
            }
        }

        private fun determineQualityTier(
            sampleRate: Int,
            bitDepth: Int,
            isLossless: Boolean
        ): AudioQuality = when {
            !isLossless -> AudioQuality.LOSSY

            // Hi-Res: 24bit+ and 96kHz+
            bitDepth >= 24 && sampleRate >= 88200 -> AudioQuality.HIRES

            // HD: 24bit at standard rates OR 16bit at high rates
            (bitDepth >= 24 && sampleRate in setOf(44100, 48000)) ||
                    (bitDepth == 16 && sampleRate >= 88200) -> AudioQuality.HD

            // CD: 16bit at standard rates
            bitDepth == 16 && sampleRate in setOf(44100, 48000) -> AudioQuality.CD

            // Fallback for non-standard combinations
            else -> AudioQuality.UNKNOWN
        }
    }
}