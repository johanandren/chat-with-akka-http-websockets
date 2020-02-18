package chat

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl._
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.typed.scaladsl.ActorSource

import scala.concurrent.ExecutionContext
import scala.io.StdIn
import scala.util.Failure
import scala.util.Success

object Server {
  def main(args: Array[String]): Unit = {
    val system: ActorSystem[Any] =
      ActorSystem(
        Behaviors.setup[Any] { context =>
          implicit val ec: ExecutionContext = context.executionContext
          val chatRoom = context.spawn(ChatRoom(), "chat")

          def newUser(): Flow[Message, Message, NotUsed] = {
            // new connection - new user actor
            val userActor = context.spawnAnonymous(User(chatRoom))

            val incomingMessages: Sink[Message, NotUsed] =
              Flow[Message]
                .map {
                  // transform websocket message to domain message
                  case TextMessage.Strict(text) => User.IncomingMessage(text)
                }
                .to(
                  ActorSink.actorRef[User.Event](
                    userActor,
                    User.Disconnected,
                    _ => User.Disconnected
                  )
                )

            val outgoingMessages: Source[Message, NotUsed] =
              ActorSource
                .actorRef[User.OutgoingMessage](
                  // never complete
                  PartialFunction.empty,
                  // never fail
                  PartialFunction.empty,
                  10,
                  OverflowStrategy.fail
                )
                .mapMaterializedValue { outActor =>
                  // give the user actor a way to send messages out
                  userActor ! User.Connected(outActor)
                  NotUsed
                }
                .map(
                  // transform domain message to web socket message
                  (outMsg: User.OutgoingMessage) => TextMessage(outMsg.text)
                )

            // then combine both to a flow
            Flow.fromSinkAndSourceCoupled(incomingMessages, outgoingMessages)
          }

          val route =
            path("chat") {
              get {
                handleWebSocketMessages(newUser())
              }
            }

          // needed until Akka HTTP has a 2.6 only release
          implicit val materializer: Materializer = SystemMaterializer(context.system).materializer
          implicit val classicSystem: akka.actor.ActorSystem = context.system.toClassic
          Http()
            .bindAndHandle(route, "127.0.0.1", 8080)
            // future callback, be careful not to touch actor state from in here
            .onComplete {
              case Success(binding) =>
                println(
                  s"Started server at ${binding.localAddress.getHostString}:${binding.localAddress.getPort}"
                )
              case Failure(ex) =>
                ex.printStackTrace()
                println("Server failed to start, terminating")
                context.system.terminate()
            }

          Behaviors.empty
        },
        "ChatServer"
      )

    println("Press enter to kill server")
    StdIn.readLine()
    system.terminate()
  }
}
