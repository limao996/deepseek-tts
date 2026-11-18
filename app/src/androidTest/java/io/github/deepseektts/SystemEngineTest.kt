package io.github.deepseektts

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.deepseektts.data.EngineSettingsStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SystemEngineTest {
 @Test fun ordinaryClientCanBindDiscoverVoicesAndFailWithoutCredential() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val store=EngineSettingsStore(context);val original=store.load()
  val initialized=CountDownLatch(1);val initialization=AtomicInteger(-99)
  var tts:TextToSpeech?=null
  try {
   store.save(original.copy(apiKey="intentionally-invalid-system-key", allowThirdPartyRequests=false))
   instrumentation.runOnMainSync { tts=TextToSpeech(context,{code->initialization.set(code);initialized.countDown()},context.packageName) }
   assertTrue("TTS initialization timed out",initialized.await(15,TimeUnit.SECONDS));assertEquals(TextToSpeech.SUCCESS,initialization.get())
   val engine=tts!!
   assertTrue(engine.engines.any{it.name==context.packageName})
   assertEquals(20,engine.voices.size)
   assertTrue(engine.voices.all{it.isNetworkConnectionRequired})
   assertTrue(engine.setLanguage(Locale.SIMPLIFIED_CHINESE)>=TextToSpeech.LANG_AVAILABLE)
   val finished=CountDownLatch(1);val error=AtomicInteger(0)
   engine.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
    override fun onStart(id:String){}
    override fun onDone(id:String){finished.countDown()}
    @Deprecated("platform callback") override fun onError(id:String){error.set(-1);finished.countDown()}
    override fun onError(id:String,code:Int){error.set(code);finished.countDown()}
   })
   assertEquals(TextToSpeech.SUCCESS,engine.speak("测试",TextToSpeech.QUEUE_FLUSH,Bundle(),"missing-credential"))
   assertTrue("Missing credential must finish with error",finished.await(10,TimeUnit.SECONDS));assertNotEquals(0,error.get());assertEquals(io.github.deepseektts.engine.EnginePhase.ERROR,io.github.deepseektts.engine.EngineStatusBus.state.value.phase)
  } finally {instrumentation.runOnMainSync{tts?.shutdown()};store.save(original)}
 }
}
