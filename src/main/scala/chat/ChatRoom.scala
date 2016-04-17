package chat

import akka.actor._

object ChatRoom {
  case object Join
  case class ChatMessage(message: String)
}

class ChatRoom extends Actor {
  import ChatRoom._
  var users: Set[ActorRef] = Set.empty

  def receive = {
    case Join =>
      users += sender()
      // we also would like to remove the user when its actor is stopped
      context.watch(sender())

    case Terminated(user) =>
      users -= user

    case msg: ChatMessage =>
      users.foreach(_ ! msg)
  }
}
