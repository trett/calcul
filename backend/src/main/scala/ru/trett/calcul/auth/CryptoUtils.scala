package ru.trett.calcul.auth

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}
import scala.util.Try

object CryptoUtils:

  private val GcmIvLengthBytes = 12
  private val GcmTagLengthBits = 128
  private val Transformation   = "AES/GCM/NoPadding"
  private val secureRandom     = new SecureRandom()

  private def deriveKey(secret: String): SecretKeySpec =
    val digest   = MessageDigest.getInstance("SHA-256")
    val keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8))
    new SecretKeySpec(keyBytes, "AES")

  def encrypt(plaintext: String, secret: String): String =
    val key = deriveKey(secret)
    val iv  = new Array[Byte](GcmIvLengthBytes)
    secureRandom.nextBytes(iv)

    val cipher = Cipher.getInstance(Transformation)
    val spec   = new GCMParameterSpec(GcmTagLengthBits, iv)
    cipher.init(Cipher.ENCRYPT_MODE, key, spec)

    val plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8)
    val cipherBytes    = cipher.doFinal(plaintextBytes)

    val combined = new Array[Byte](iv.length + cipherBytes.length)
    System.arraycopy(iv, 0, combined, 0, iv.length)
    System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length)

    Base64.getEncoder.encodeToString(combined)

  def decrypt(ciphertextBase64: String, secret: String): Either[String, String] =
    Try {
      val combined = Base64.getDecoder.decode(ciphertextBase64)
      if combined.length < GcmIvLengthBytes then
        throw new IllegalArgumentException("Ciphertext is too short to contain IV")

      val iv          = new Array[Byte](GcmIvLengthBytes)
      val cipherBytes = new Array[Byte](combined.length - GcmIvLengthBytes)
      System.arraycopy(combined, 0, iv, 0, GcmIvLengthBytes)
      System.arraycopy(combined, GcmIvLengthBytes, cipherBytes, 0, cipherBytes.length)

      val key    = deriveKey(secret)
      val cipher = Cipher.getInstance(Transformation)
      val spec   = new GCMParameterSpec(GcmTagLengthBits, iv)
      cipher.init(Cipher.DECRYPT_MODE, key, spec)

      val decryptedBytes = cipher.doFinal(cipherBytes)
      new String(decryptedBytes, StandardCharsets.UTF_8)
    }.toEither.left.map(_.getMessage)

  def maskKey(key: String): String =
    if key.length <= 4 then "••••"
    else
      val last4 = key.takeRight(4)
      s"••••••••••••$last4"
