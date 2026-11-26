package io.github.trunghuynhvan0310.signingkeyvault

import io.github.trunghuynhvan0310.signingkeyvault.crypto.KeystoreGenerator
import io.github.trunghuynhvan0310.signingkeyvault.crypto.KeystoreRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import java.io.ByteArrayInputStream
import java.security.KeyStore
import org.junit.Test

class KeystoreGeneratorTest {
    private val request = KeystoreRequest(
        alias = "release",
        commonName = "Test App",
        organization = "Test",
        country = "VN",
        validityYears = 30,
        keyBits = 2048,
    )

    @Test
    fun generatedKeystoreCanBeInspected() {
        val password = KeystoreGenerator.generatePassword()
        val generated = KeystoreGenerator.generate(request, password)
        val info = KeystoreGenerator.inspect(generated.bytes, password, password, "release")
        assertEquals("release", info.alias)
        assertEquals(generated.info.sha256, info.sha256)
        assertEquals(95, info.sha256.length) // 32 byte dạng AA:BB:...
        assertTrue(info.validUntil > System.currentTimeMillis() + 25L * 365 * 24 * 3600 * 1000)
    }

    // Gradle/apksigner đọc keystore bằng JDK, nên phải kiểm tra tương thích với bộ đọc PKCS12 chuẩn.
    @Test
    fun generatedKeystoreIsReadableByStandardJdk() {
        val password = KeystoreGenerator.generatePassword()
        val generated = KeystoreGenerator.generate(request, password)
        val store = KeyStore.getInstance("PKCS12")
        store.load(ByteArrayInputStream(generated.bytes), password.toCharArray())
        val alias = store.aliases().nextElement()
        assertNotNull(store.getKey(alias, password.toCharArray()))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val generated = KeystoreGenerator.generate(request, KeystoreGenerator.generatePassword())
        assertThrows(Exception::class.java) {
            KeystoreGenerator.inspect(generated.bytes, "wrong-password", "wrong-password", null)
        }
    }

    @Test
    fun passwordsAreDistinctAndLongEnough() {
        val a = KeystoreGenerator.generatePassword()
        val b = KeystoreGenerator.generatePassword()
        assertEquals(24, a.length)
        assertTrue(a != b)
    }
}
