package com.example.model

import android.net.Uri
import java.security.SecureRandom

object RoomCodeGenerator {
    // Exclude visually/audibly ambiguous characters: 0, O, 1, I, L
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 6
    private val random = SecureRandom()

    fun generate(): String {
        val chars = CharArray(CODE_LENGTH) {
            ALPHABET[random.nextInt(ALPHABET.length)]
        }
        return String(chars)
    }

    fun normalize(raw: String): String {
        return raw.trim()
            .uppercase()
            .filter { it in 'A'..'Z' || it in '0'..'9' }
            .take(CODE_LENGTH)
    }

    fun isValid(code: String): Boolean {
        return code.length == CODE_LENGTH && code.all { it in 'A'..'Z' || it in '2'..'9' }
    }

    fun buildDeepLink(roomCode: String): String = "runtogether://join/$roomCode"

    fun buildWebLink(roomCode: String): String = "https://runtogether.app/join/$roomCode"

    fun buildShareMessage(displayName: String, roomCode: String): String {
        return buildString {
            append("$displayName invited you to Run Together!\n\n")
            append("Room code: $roomCode\n")
            append("Open in app: ${buildDeepLink(roomCode)}\n")
            append("Link: ${buildWebLink(roomCode)}")
        }
    }

    fun extractRoomCodeFromUri(uri: Uri?): String? {
        if (uri == null) return null
        val segment = when {
            uri.scheme == "runtogether" && uri.host == "join" -> uri.pathSegments.firstOrNull()
            uri.scheme == "https" && uri.host == "runtogether.app" -> {
                if (uri.pathSegments.firstOrNull() == "join") uri.pathSegments.getOrNull(1) else null
            }
            else -> uri.getQueryParameter("code")
        } ?: return null
        val normalized = normalize(segment)
        return normalized.takeIf { isValid(it) }
    }
}
