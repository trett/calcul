package ru.trett.calcul.auth

import munit.FunSuite

class CryptoUtilsSuite extends FunSuite:

  val secret = "test-secret-encryption-key-12345"

  test("encrypt and decrypt round-trip correctly") {
    val plaintext = "AIzaSyD-sample-gemini-api-key-test-987654321"
    val encrypted = CryptoUtils.encrypt(plaintext, secret)
    assert(encrypted.nonEmpty, "Encrypted string must not be empty")
    assert(encrypted != plaintext, "Encrypted string must not equal plaintext")

    val decrypted = CryptoUtils.decrypt(encrypted, secret)
    assertEquals(decrypted, Right(plaintext))
  }

  test("decrypt fails with incorrect secret") {
    val plaintext = "AIzaSyD-sample-gemini-api-key"
    val encrypted = CryptoUtils.encrypt(plaintext, secret)
    val result    = CryptoUtils.decrypt(encrypted, "wrong-secret-key-67890")
    assert(result.isLeft, "Decryption with wrong secret must fail")
  }

  test("decrypt fails with malformed ciphertext") {
    val result = CryptoUtils.decrypt("not-a-valid-base64-ciphertext!@#", secret)
    assert(result.isLeft, "Decryption of malformed input must fail")
  }

  test("maskKey correctly masks API key showing only last 4 characters") {
    val key    = "AIzaSyD1234567890abcdef"
    val masked = CryptoUtils.maskKey(key)
    assertEquals(masked, "••••••••••••cdef")
  }

  test("maskKey handles short keys safely") {
    val key    = "abc"
    val masked = CryptoUtils.maskKey(key)
    assertEquals(masked, "••••")
  }
