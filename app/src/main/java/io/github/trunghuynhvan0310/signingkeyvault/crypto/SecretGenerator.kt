package io.github.trunghuynhvan0310.signingkeyvault.crypto

import java.util.Base64

object SecretGenerator {
    enum class Kind { Hex32, Base64_32, Password }


    fun generate(kind: Kind): String = when (kind) {
        Kind.Hex32 -> Crypto.randomBytes(32).joinToString("") { "%02x".format(it) }
        Kind.Base64_32 -> Base64.getEncoder().encodeToString(Crypto.randomBytes(32))
        Kind.Password -> KeystoreGenerator.generatePassword(32)
    }
}
