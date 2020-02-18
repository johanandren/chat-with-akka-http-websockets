package chat

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object User {
  sealed trait Event
  case class Connected(outgoing: ActorRef[OutgoingMessage]) extends Event
  case object Disconnected extends Event
  case class IncomingMessage(text: String) extends Event
  case class OutgoingMessage(text: String) extends Event

  def apply(chatRoom: ActorRef[ChatRoom.Command]): Behavior[Event] =
      waitingForConnection(chatRoom)

    private def waitingForConnection(chatRoom: ActorRef[ChatRoom.Command]): Behavior[Event] =
      Behaviors.setup { context =>
        Behaviors.receiveMessagePartial {
          case Connected(outgoing) =>
            chatRoom ! ChatRoom.Join(context.messageAdapter {
              case ChatRoom.ChatMessage(text) => OutgoingMessage(text)
            })
            connected(chatRoom, outgoing)
        }
      }

  private def connected(chatRoom: ActorRef[ChatRoom.Command], outgoing: ActorRef[OutgoingMessage]): Behavior[Event] =
    Behaviors.receiveMessagePartial {
      case IncomingMessage(text) =>
        chatRoom ! ChatRoom.ChatMessage(text)
        Behaviors.same
      case msg: OutgoingMessage =>
        outgoing ! msg
        Behaviors.same
      case Disconnected =>
        Behaviors.stopped
    }
}