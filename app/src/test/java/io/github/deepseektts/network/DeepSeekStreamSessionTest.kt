package io.github.deepseektts.network

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeepSeekStreamSessionTest {
 private lateinit var server: MockWebServer
 private lateinit var client: OkHttpClient
 @Before fun setup() { server=MockWebServer(); server.start(); client=OkHttpClient.Builder().readTimeout(0,TimeUnit.MILLISECONDS).build() }
 @After fun cleanup() { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll(); server.shutdown() }
 private fun session(timeout:Long=2000)=DeepSeekStreamSession(client,server.url("/v1/tools/tts").toString(),timeout,5000)
 private abstract class ClosingListener : WebSocketListener() {
  val closed=CountDownLatch(1)
  override fun onClosing(webSocket:WebSocket,code:Int,reason:String){ webSocket.close(code,null) }
  override fun onClosed(webSocket:WebSocket,code:Int,reason:String){ closed.countDown() }
 }
 @Test fun sendsAuthenticatedTextAndStreamsBeforeFinish() {
  val first=CountDownLatch(1); val finish=CountDownLatch(1); val messages=mutableListOf<String>()
  val listener=object:ClosingListener(){
   override fun onMessage(ws:WebSocket,text:String){ synchronized(messages){messages.add(text)}; if(text==TtsProtocol.FINISH_TEXT){ws.send(byteArrayOf(1,2,3,4).toByteString()); first.countDown(); Thread{finish.await(3,TimeUnit.SECONDS);ws.send("{\"event\":\"finish\",\"code\":\"success\"}")}.start()} }
  }
  server.enqueue(MockResponse().withWebSocketUpgrade(listener))
  val stream=session();stream.start("test-secret","tide",false,"你好")
  assertTrue(first.await(3,TimeUnit.SECONDS))
  assertArrayEquals(byteArrayOf(1,2,3,4),(stream.next() as DeepSeekStreamSession.Event.Audio).bytes)
  finish.countDown();assertEquals(DeepSeekStreamSession.Event.Complete,stream.next())
  val request=server.takeRequest(2,TimeUnit.SECONDS)!!
  assertEquals("Bearer test-secret",request.getHeader("Authorization"));assertTrue(request.path!!.contains("voice_id=tide"));assertTrue(request.path!!.contains("format=pcm"));assertTrue(request.path!!.contains("filter_markdown=false"))
  assertEquals(2,synchronized(messages){messages.size});assertTrue(listener.closed.await(3,TimeUnit.SECONDS));stream.cancel()
 }
 @Test fun completionDrainsEveryQueuedAudioBlock() {
  val input=ByteArray(200_000){it.toByte()}
  val listener=object:ClosingListener(){override fun onMessage(ws:WebSocket,text:String){if(text==TtsProtocol.FINISH_TEXT){ws.send(input.toByteString());ws.send("{\"event\":\"finish\",\"code\":\"success\"}")}}};server.enqueue(MockResponse().withWebSocketUpgrade(listener))
  val s=session();s.start("test","stella",true,"a");val received=java.io.ByteArrayOutputStream()
  while(true){when(val event=s.next()){is DeepSeekStreamSession.Event.Audio->received.write(event.bytes);DeepSeekStreamSession.Event.Complete->break;else->fail("Unexpected terminal: $event")}}
  assertArrayEquals(input,received.toByteArray());assertTrue(listener.closed.await(3,TimeUnit.SECONDS));s.cancel()
 }
 @Test fun abnormalCloseIsNotSuccess() {
  server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener(){override fun onOpen(ws:WebSocket,response:Response){ws.close(1000,"early")}}))
  val s=session();s.start("test","stella",true,"a");assertTrue(s.next() is DeepSeekStreamSession.Event.Failure);s.cancel()
 }
 @Test fun largeAudioBurstDoesNotFailOnNormalBackpressure() {
  val block=ByteArray(300_000){it.toByte()}
  val listener=object:ClosingListener(){override fun onMessage(ws:WebSocket,text:String){if(text==TtsProtocol.FINISH_TEXT){repeat(8){ws.send(block.toByteString())};ws.send("{\"event\":\"finish\",\"code\":\"success\"}")}}};server.enqueue(MockResponse().withWebSocketUpgrade(listener))
  val s=session();s.start("test","stella",true,"a");var received=0
  while(true){when(val event=s.next()){is DeepSeekStreamSession.Event.Audio->received+=event.bytes.size;DeepSeekStreamSession.Event.Complete->break;else->fail("Unexpected terminal: $event")}}
  assertEquals(2_400_000,received);assertTrue(listener.closed.await(3,TimeUnit.SECONDS));s.cancel()
 }
 @Test fun cancelUnblocksBackpressuredProducer() {
  val sent=CountDownLatch(1)
  server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener(){override fun onMessage(ws:WebSocket,text:String){if(text==TtsProtocol.FINISH_TEXT){repeat(8){ws.send(ByteArray(300_000).toByteString())};sent.countDown()}}}))
  val s=session();s.start("test","stella",true,"a");assertTrue(sent.await(2,TimeUnit.SECONDS));s.cancel();assertEquals(DeepSeekStreamSession.Event.Cancelled,s.next())
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsUnsafeCredentialBeforeNetwork() {session().start("secret\nheader","stella",true,"a")}
 @Test fun rateLimitDoesNotAutomaticallyRetry() {
  server.enqueue(MockResponse().setResponseCode(429));val s=session();s.start("test","stella",true,"a");assertEquals(DeepSeekStreamSession.Reason.RATE_LIMIT,(s.next() as DeepSeekStreamSession.Event.Failure).reason);assertEquals(1,server.requestCount);s.cancel()
 }
 @Test fun authFailureIsActionable() {server.enqueue(MockResponse().setResponseCode(401));val s=session();s.start("test","stella",true,"a");assertEquals(DeepSeekStreamSession.Reason.AUTH,(s.next() as DeepSeekStreamSession.Event.Failure).reason);s.cancel()}
 @Test fun serverFailureIsNotComplete() {server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener(){override fun onOpen(ws:WebSocket,response:Response){ws.send("{\"event\":\"finish\",\"code\":\"content_filter\"}")}}));val s=session();s.start("test","stella",true,"a");assertEquals(DeepSeekStreamSession.Reason.CONTENT_FILTER,(s.next() as DeepSeekStreamSession.Event.Failure).reason);s.cancel()}
 @Test fun malformedServerEventTerminatesAsProtocolError() {
  server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener(){override fun onOpen(ws:WebSocket,response:Response){ws.send("not-json")}}))
  val s=session();s.start("test","stella",true,"a");assertEquals(DeepSeekStreamSession.Reason.PROTOCOL,(s.next() as DeepSeekStreamSession.Event.Failure).reason);s.cancel()
 }
 @Test fun overallSessionTimeoutTerminates() {server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener(){}));val s=DeepSeekStreamSession(client,server.url("/v1/tools/tts").toString(),60_000,100);s.start("test","stella",true,"a");assertEquals(DeepSeekStreamSession.Reason.TIMEOUT,(s.next() as DeepSeekStreamSession.Event.Failure).reason);s.cancel()}
 @Test fun inputIdleTimeoutUsesDocumentedSixtySeconds() { server.enqueue(MockResponse().setHeadersDelay(250,TimeUnit.MILLISECONDS).withWebSocketUpgrade(object:WebSocketListener(){})); val s=DeepSeekStreamSession(client,server.url("/v1/tools/tts").toString(),100,5_000);s.start("test","stella",true,"a");assertEquals(DeepSeekStreamSession.Reason.TIMEOUT,(s.next() as DeepSeekStreamSession.Event.Failure).reason);s.cancel() }
 @Test fun slowFirstAudioAfterFinishTextIsNotKilledByClientIdleTimer() {
  val listener=object:ClosingListener(){override fun onMessage(ws:WebSocket,text:String){if(text==TtsProtocol.FINISH_TEXT){Thread{Thread.sleep(250);ws.send(byteArrayOf(1,2).toByteString());ws.send("{\"event\":\"finish\",\"code\":\"success\"}")}.start()}}};server.enqueue(MockResponse().withWebSocketUpgrade(listener))
  val s=session(100);s.start("test","stella",true,"a");assertArrayEquals(byteArrayOf(1,2),(s.next() as DeepSeekStreamSession.Event.Audio).bytes);assertEquals(DeepSeekStreamSession.Event.Complete,s.next());s.cancel()
 }
 @Test fun cancelBeforeStartCannotLeakConnection() {val s=session();s.cancel();s.start("test","stella",true,"a");assertEquals(DeepSeekStreamSession.Event.Cancelled,s.next());assertNull(server.takeRequest(300,TimeUnit.MILLISECONDS));assertEquals(0,server.requestCount)}
 @Test fun cancellationInterruptsBlockedConsumer() {server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener(){}));val s=session();s.start("test","stella",true,"a");val done=CountDownLatch(1);var result:DeepSeekStreamSession.Event?=null;val worker=Thread{result=s.next();done.countDown()};worker.start();s.cancel();assertTrue(done.await(1,TimeUnit.SECONDS));assertEquals(DeepSeekStreamSession.Event.Cancelled,result)}
}
