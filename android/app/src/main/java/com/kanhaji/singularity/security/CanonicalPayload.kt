package com.kanhaji.singularity.security

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

object CanonicalPayload {
    private const val PROTOCOL_HEADER = "SINGULARITY_AUTH_V1"

    fun build(
        nonce: ByteArray,
        timestampSecs: Long,
        counter: Long,
        service: String,
        user: String
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        dos.write(PROTOCOL_HEADER.toByteArray(StandardCharsets.UTF_8))
        dos.writeInt(nonce.size)
        dos.write(nonce)
        dos.writeLong(timestampSecs)
        dos.writeLong(counter)
        dos.writeUTF(service)
        dos.writeUTF(user)
        dos.flush()

        return bos.toByteArray()
    }
}

