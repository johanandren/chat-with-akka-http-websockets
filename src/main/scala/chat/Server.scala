package chat

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.StdIn

object Server {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    // chat room many clients -> merge hub -> broadcasthub -> many clients
    val (chatSink, chatSource) =
      MergeHub.source[String].toMat(BroadcastHub.sink[String])(Keep.both).run()

    val userFlow: Flow[Message, Message, NotUsed] =
      Flow[Message].mapAsync(1) {
        // transform websocket message to domain message (string)
        case TextMessage.Strict(text) =>       Future.successful(text)
        case streamed: TextMessage.Streamed => streamed.textStream.runFold("")(_ ++ _)
      }.via(Flow.fromSinkAndSource(chatSink, chatSource))
       .map[Message](string => TextMessage(string))

    val route =
      path("chat") {
        get {
          handleWebSocketMessages(userFlow)
        }
      }

    val binding = Await.result(Http().bindAndHandle(route, "127.0.0.1", 8080), 3.seconds)

    // the rest of the sample code will go here
    println("Started server at 127.0.0.1:8080, press enter to kill server")
    StdIn.readLine()
    system.terminate()
  }
}
