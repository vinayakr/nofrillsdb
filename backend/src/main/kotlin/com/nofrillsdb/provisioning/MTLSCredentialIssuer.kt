package com.nofrillsdb.provisioning

import com.fasterxml.uuid.Generators
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.io.StringReader
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date

@Component
class MTLSCredentialIssuer {

    @Value("\${provisioning.client-ca-key}")
    val clientCaKey: String? = null

    @Value("\${provisioning.client-ca-crt-location}")
    private val clientCrtLocation: String = "certs/clients_ca.crt"

    private val caCertPem: String by lazy {
        ClassPathResource(clientCrtLocation).inputStream.bufferedReader().use { it.readText() }
    }


    constructor() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }


    fun derToPem(derBase64: String): String {
        val der = Base64.getDecoder().decode(derBase64.trim())
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)
        return buildString {
            append("-----BEGIN PRIVATE KEY-----\n")
            append(b64)
            append("\n-----END PRIVATE KEY-----\n")
        }
    }
    fun generateRoleId(): String {
        val uuidV7 = Generators.timeBasedEpochGenerator().generate()
        return "role_${uuidV7.toString().replace("-", "")}"
    }

    fun issueClientCredential(
        role: String,
        validityDays: Long = 365,
        keyAlgorithm: String = "RSA",
        rsaBits: Int = 2048,
    ): IssuedClientCredential {
        require(isSafeRole(role)) { "Role contains invalid characters or is too long: $role" }

        val caCertHolder = parseX509CertPem(caCertPem)

        if (clientCaKey == null) {
            throw IllegalStateException("Client CA key is not configured")
        }

        val caPrivateKey = parsePrivateKeyPem(derToPem(clientCaKey!!))

        val clientKeyPair = generateKeyPair(keyAlgorithm, rsaBits)

        val now = Instant.now()
        val notBefore = now.minus(5, ChronoUnit.MINUTES)
        val notAfter = now.plus(validityDays, ChronoUnit.DAYS)

        val serial = randomSerial()

        val subject = X500Name("CN=$role")
        val issuer = caCertHolder.subject

        val builder = X509v3CertificateBuilder(
            issuer,
            serial,
            Date.from(notBefore),
            Date.from(notAfter),
            subject,
            SubjectPublicKeyInfo.getInstance(clientKeyPair.public.encoded)
        )

        val extUtils = JcaX509ExtensionUtils()

        // ---- X.509 extensions (client cert) ----
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))

        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )

        builder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth)
        )

        // Subject Key Identifier + Authority Key Identifier
        builder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extUtils.createSubjectKeyIdentifier(clientKeyPair.public)
        )
        builder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            extUtils.createAuthorityKeyIdentifier(caCertHolder)
        )


        val signer: ContentSigner = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider("BC")
            .build(caPrivateKey)

        val issuedHolder: X509CertificateHolder = builder.build(signer)
        val issuedCert: X509Certificate = JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(issuedHolder)

        // Basic sanity check
        issuedCert.checkValidity(Date.from(now))
        issuedCert.verify(parsePublicKeyFromCert(caCertHolder))

        val clientKeyPem = toPem(clientKeyPair.private)
        val clientCertPem = toPem(issuedCert)

        val fingerprint = sha256FingerprintHex(issuedCert.encoded)

        return IssuedClientCredential(
            role = role,
            privateKeyPem = clientKeyPem,
            certificatePem = clientCertPem,
            serialHex = serial.toString(16).uppercase(),
            fingerprintSha256Hex = fingerprint,
            issuedAt = now,
            expiresAt = notAfter
        )
    }

    // ---------------- helpers ----------------

    private fun isSafeRole(role: String): Boolean {
        // Postgres identifier max length is 63 bytes; keep it <= 63 chars ASCII
        if (role.length !in 3..63) return false
        // Keep it simple: lowercase letters, digits, underscore only
        return role.matches(Regex("^[a-z0-9_]+$"))
    }

    private fun generateKeyPair(algorithm: String, rsaBits: Int): KeyPair {
        val gen = KeyPairGenerator.getInstance(algorithm)
        if (algorithm.equals("RSA", ignoreCase = true)) {
            gen.initialize(rsaBits, SecureRandom())
        } else {
            // for EC you’d configure curve params; leaving RSA as default for widest client compatibility
            gen.initialize(256, SecureRandom())
        }
        return gen.generateKeyPair()
    }

    private fun randomSerial(): BigInteger {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        // make it positive
        return BigInteger(1, bytes)
    }

    private fun parseX509CertPem(pem: String): X509CertificateHolder {
        PEMParser(StringReader(pem)).use { parser ->
            val obj = parser.readObject()
            require(obj is X509CertificateHolder) { "Expected X509CertificateHolder in CA cert PEM" }
            return obj
        }
    }

    private fun parsePrivateKeyPem(pem: String): PrivateKey {
        PEMParser(StringReader(pem)).use { parser ->
            val obj = parser.readObject()
            val converter = JcaPEMKeyConverter().setProvider("BC")

            return when (obj) {
                is PEMKeyPair -> converter.getKeyPair(obj).private
                is org.bouncycastle.asn1.pkcs.PrivateKeyInfo -> converter.getPrivateKey(obj)
                else -> error("Unsupported private key PEM object: ${obj?.javaClass?.name}")
            }
        }
    }

    private fun parsePublicKeyFromCert(holder: X509CertificateHolder) =
        JcaX509CertificateConverter().setProvider("BC").getCertificate(holder).publicKey

    private fun toPem(any: Any): String {
        val sw = StringWriter()
        JcaPEMWriter(sw).use { it.writeObject(any) }
        return sw.toString()
    }

    private fun sha256FingerprintHex(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

}