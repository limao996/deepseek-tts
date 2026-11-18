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
 @Test fun dailyQuotaCapsWorstCaseCharactersAndResetsByUtcDay() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val store=EngineSettingsStore(context);val original=store.load()
  try { val day=1_900_000_000_000L; assertTrue(store.reserveDailyCharacters(9_000,day).allowed); assertFalse(store.reserveDailyCharacters(1_001,day).allowed); assertTrue(store.reserveDailyCharacters(1_000,day+86_400_000L).allowed) } finally {store.save(original)}
 }
 @Test fun sonicStreamsAndAltersDurationWithoutChangingFormat() {
  val buffer=ByteBuffer.allocate(48_000).order(ByteOrder.LITTLE_ENDIAN)
  repeat(24_000){buffer.putShort((sin(2*PI*440*it/24000)*8000).toInt().toShort())}
  val input=buffer.array();var bytes=0;var beforeFinish=0
  val processor=PcmProcessor(2f,1f){assertEquals(0,it.size%2);bytes+=it.size}
  try { input.toList().chunked(1021).forEach{processor.accept(it.toByteArray())};beforeFinish=bytes;processor.finish();assertTrue(beforeFinish>0);assertTrue(bytes in 20_000..28_000) } finally {processor.release()}
 }
 @Test fun unitySpeedPreservesPcmExactly() {
  val input=ByteArray(4800){it.toByte()};val chunks=mutableListOf<Byte>();val processor=PcmProcessor(1f,1f){chunks.addAll(it.toList())}
  try {input.toList().chunked(77).forEach{processor.accept(it.toByteArray())};processor.finish();assertArrayEquals(input,chunks.toByteArray())}finally{processor.release()}
 }
}
