import akka.actor.{ ActorSystem, Props }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import akka.util.ByteString
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import scala.annotation.tailrec
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import xyz.thomaslee.yojik.tls.{ TlsActor, TlsListeningActor }

class TlsListeningActorSpec extends TestKit(ActorSystem("TlsListeningActorSpec"))
    with MockFactory
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  class TestTlsChannel(data: List[ByteBuffer]) extends ByteChannel {
    var count = 0

    def read(buffer: ByteBuffer): Int = count match {
      case index if index < data.size => {
        count += 1
        val bytesRead = data(index).limit
        buffer.put(data(index))
        bytesRead
      }
      case _ => -1
    }

    def write(buffer: ByteBuffer): Int = 0
    def close: Unit = {}
    def isOpen: Boolean = true
  }

def removePaddingFromDecryptedBytes(bytes: ByteString): ByteString = {
  @tailrec
  def findLastNulIndex(index: Int): Int =
    if (index == 0) {
      bytes.length
    }
    else if (bytes(index) != 0) {
      index + 1
    }
    else {
      findLastNulIndex(index - 1)
    }

  bytes.take(findLastNulIndex(bytes.length - 1))
}

  "TlsListeningActor" must {
    "reads all available data from the TlsChannel when Listen received" in {
      val data = List(
        ByteString("yojik"),
        ByteString("ёжик")
      )

      val tlsActor = TestProbe("TlsActor")
      val tlsChannel = new TestTlsChannel(data.map(
        datum => ByteBuffer.wrap(datum.toArray[Byte])))
      val tlsListener = system.actorOf(Props(new TlsListeningActor(tlsChannel) {
        override def receive: Receive = listenForMessages(tlsActor.ref)
      }))

      tlsListener ! TlsListeningActor.Listen

      for (datum <- data) {
        println(datum.utf8String)
        tlsActor.expectMsgPF(200 millis) {
          case TlsActor.SendToServer(message) if
            removePaddingFromDecryptedBytes(message) == datum => ()
        }
      }
    }
  }
}
