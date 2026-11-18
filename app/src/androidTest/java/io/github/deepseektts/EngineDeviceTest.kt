package io.github.deepseektts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.util.UnstableApi
import io.github.deepseektts.audio.PcmProcessor
import io.github.deepseektts.data.EngineSettings
import io.github.deepseektts.data.EngineSettingsStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
@UnstableApi
class EngineDeviceTest {
 @Test fun secureSettingsRoundTripAndClear() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val store=EngineSettingsStore(context); val original=store.load()
  try { val value=EngineSettings(apiKey="test-device-only-not-real",voiceId="tide",speechRate=1.5f,pitch=0.8f);store.save(value);assertEquals(value,store.load())
   val preferences=context.getSharedPreferences("engine_settings",0);assertFalse(preferences.all.values.any{it.toString().contains(value.apiKey)})
   store.save(value.copy(apiKey=""));assertEquals("",store.load().apiKey)
  } finally {store.save(original)}
 }
 @Test fun corruptedCredentialRequiresExplicitRecovery() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val store=EngineSettingsStore(context);val original=store.load()
  try {context.getSharedPreferences("engine_settings",0).edit().putString("credential","broken-value").commit();try{store.load();fail("Expected storage recovery error")}catch(_:io.github.deepseektts.data.SettingsStorageException){};store.save(original.copy(apiKey=""));assertEquals("",store.load().apiKey)}finally{store.save(original)}
 }
 @Test fun catalogueSupportsOfficialLanguagesAndRejectsUnknown() {
  val catalogue=io.github.deepseektts.engine.VoiceCatalog
  catalogue.locales.forEach{assertTrue(catalogue.availability(it.isO3Language,it.isO3Country)>=0)}
  assertEquals(android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED,catalogue.availability("deu","DEU"))
  assertEquals(20,catalogue.voices.size)
 }
 @Test fun sonicStreamsAndAltersDurationWithoutChangingFormat() {
  val buffer=ByteBuffer.allocate(48_000).order(ByteOrder.LITTLE_ENDIAN)
  repeat(24_000){buffer.putShort((sin(2*PI*440*it/24000)*8000).toInt().toShort())}
  val input=buffer.array();var bytes=0;var beforeFinish=0
  val processor=PcmProcessor(2f,1f){assertEquals(0,it.size%2);bytes+=it.size}
  try { input.toList().chunked(1021).forEach{processor.accept(it.toByteArray())};beforeFinish=bytes;processor.finish();assertTrue(beforeFinish>0);assertTrue(bytes in 20_000..28_000) } finally {processor.release()}
 }
 @Test fun thirdPartyDefaultRateUsesConfiguredBaseline() {
  // Android sends 100 when a reader app never calls setSpeechRate(); the app's own setting must win.
  assertEquals(1.5f, io.github.deepseektts.audio.PlaybackRate.effective(100, 1.5f, applyBaseline = true), 1e-6f)
  assertEquals(0.8f, io.github.deepseektts.audio.PlaybackRate.effective(100, 0.8f, applyBaseline = true), 1e-6f)
  // The in-app preview already passes the configured value, so it must not be multiplied twice.
  assertEquals(1.5f, io.github.deepseektts.audio.PlaybackRate.effective(150, 1.5f, applyBaseline = false), 1e-6f)
 }
 @Test fun platformSpeakCapStillAllowsOfficialCharacterLimit() {
  // Android caps TextToSpeech.speak() per call; the engine accepts 10,000 billed characters.
  val max = android.speech.tts.TextToSpeech.getMaxSpeechInputLength()
  assertTrue("Platform cap should be below the official limit on this API level", max < 10_000)
  val text = "字".repeat(5_000) // exactly 10,000 billed characters
  assertEquals(10_000, io.github.deepseektts.network.TtsProtocol.billedCharacters(text))
  io.github.deepseektts.network.TtsProtocol.textMessage(text) // must not throw
  val chunks = io.github.deepseektts.network.TtsProtocol.chunkForPlayback(text, max)
  assertTrue(chunks.size >= 2)
  assertTrue(chunks.all { it.length <= max })
  assertEquals(text, chunks.joinToString(""))
 }
 @Test fun unitySpeedPreservesPcmExactly() {
  val input=ByteArray(4800){it.toByte()};val chunks=mutableListOf<Byte>();val processor=PcmProcessor(1f,1f){chunks.addAll(it.toList())}
  try {input.toList().chunked(77).forEach{processor.accept(it.toByteArray())};processor.finish();assertArrayEquals(input,chunks.toByteArray())}finally{processor.release()}
 }
}
