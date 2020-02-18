package chat

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.Terminated
import akka.actor.typed.scaladsl.Behaviors

object ChatRoom {
  sealed trait Command
  case class Join(user: ActorRef[ChatMessage]) extends Command
  case class ChatMessage(message: String) extends Command

  def apply(): Behavior[Command] = run(Set.empty)

  private def run(users: Set[ActorRef[ChatMessage]]): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage[Command] {
        case Join(user) =>
          // watch so we can remove the user when if actor is stopped
          context.watch(user)
          run(users + user)
        case msg: ChatMessage =>
          users.foreach(_ ! msg)
          Behaviors.same
      }.receiveSignal {
        case (__, Terminated(user)) =>
          run(users.filterNot(_ == user))
      }
    }
}
