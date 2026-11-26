package io.github.trunghuynhvan0310.signingkeyvault.crypto

import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Date

data class KeystoreRequest(
    val alias: String,
    val commonName: String,
    val organization: String,
    val country: String,
    val validityYears: Int,
    val keyBits: Int,
)

data class KeystoreInfo(
    val alias: String,
    val subject: String,
    val sha1: String,
    val sha256: String,
    val validUntil: Long,
)

class GeneratedKeystore(val bytes: ByteArray, val password: String, val info: KeystoreInfo)

/**
 * Khóa của Android Keystore không xuất được, nên khóa ký được sinh bằng phần mềm (Bouncy Castle)
 * rồi đóng gói PKCS12 để người dùng sao lưu và dán vào GitHub Secrets.
 */
object KeystoreGenerator {
    private val provider = BouncyCastleProvider()
    private val random = SecureRandom()
    private const val PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

    fun generatePassword(length: Int = 24): String =
        buildString { repeat(length) { append(PASSWORD_ALPHABET[random.nextInt(PASSWORD_ALPHABET.length)]) } }

    /** PKCS12 chỉ hỗ trợ một mật khẩu chung cho kho và khóa. */
    fun generate(request: KeystoreRequest, password: String): GeneratedKeystore {
        val generator = KeyPairGenerator.getInstance("RSA", provider)
        generator.initialize(request.keyBits, random)
        val pair = generator.generateKeyPair()

        val name = X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.CN, request.commonName.ifBlank { request.alias })
            if (request.organization.isNotBlank()) addRDN(BCStyle.O, request.organization)
            if (request.country.length == 2) addRDN(BCStyle.C, request.country.uppercase())
        }.build()

        val now = System.currentTimeMillis()
        val notAfter = now + request.validityYears * 365L * 24 * 3600 * 1000
        val serial = BigInteger(64, random).add(BigInteger.ONE)
        val builder = JcaX509v3CertificateBuilder(name, serial, Date(now - 60_000), Date(notAfter), name, pair.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(pair.private)
        val certificate = JcaX509CertificateConverter().setProvider(provider).getCertificate(builder.build(signer))

        val store = KeyStore.getInstance("PKCS12", provider)
        store.load(null, null)
        store.setKeyEntry(request.alias, pair.private, password.toCharArray(), arrayOf<Certificate>(certificate))
        val out = ByteArrayOutputStream()
        store.store(out, password.toCharArray())
        return GeneratedKeystore(out.toByteArray(), password, describe(request.alias, certificate))
    }

    /**
     * Mở keystore có sẵn (PKCS12, thử thêm JKS) và đọc thông tin chứng chỉ.
     * Ném ngoại lệ nếu sai mật khẩu hoặc không đọc được.
     */
    fun inspect(bytes: ByteArray, storePassword: String, keyPassword: String, alias: String?): KeystoreInfo {
        var failure: Exception? = null
        for (type in listOf("PKCS12", "JKS")) {
            try {
                val store = KeyStore.getInstance(type, provider)
                store.load(ByteArrayInputStream(bytes), storePassword.toCharArray())
                val found = alias?.takeIf { it.isNotBlank() && store.containsAlias(it) }
                    ?: store.aliases().toList().firstOrNull { store.isKeyEntry(it) }
                    ?: throw IllegalArgumentException("No key entry")
                store.getKey(found, keyPassword.toCharArray()) ?: throw IllegalArgumentException("No key")
                return describe(found, store.getCertificate(found) as X509Certificate)
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: IllegalArgumentException("Unreadable keystore")
    }

    private fun describe(alias: String, certificate: X509Certificate) = KeystoreInfo(
        alias = alias,
        subject = certificate.subjectX500Principal.name,
        sha1 = fingerprint("SHA-1", certificate),
        sha256 = fingerprint("SHA-256", certificate),
        validUntil = certificate.notAfter.time,
    )

    private fun fingerprint(algorithm: String, certificate: X509Certificate): String =
        MessageDigest.getInstance(algorithm).digest(certificate.encoded)
            .joinToString(":") { "%02X".format(it) }
}
