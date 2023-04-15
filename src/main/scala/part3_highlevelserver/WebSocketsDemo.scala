package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Rejection, RejectionHandler, Route}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.{CompactByteString, Timeout}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import spray.json._

object WebSocketsDemo extends App {
  implicit val system = ActorSystem("Web-Socket-Demo")
  import system.dispatcher

  // Message: TextMessage vs BinaryMessage
  val textMessage = TextMessage(Source.single("hello via s text message"))
  val binaryMessage = BinaryMessage(
    Source.single(CompactByteString("hello via a binary message"))
  )

  val html =
    """
      |<html>
      |    <head>
      |        <script>
      |            var exampleSocket = new WebSocket("ws://localhost:8080/greeter");
      |            console.log("starting websocket...");
      |
      |            exampleSocket.onmessage = function(event) {
      |                var newChild = document.createElement("div");
      |                newChild.innerText = event.data;
      |                document.getElementById("1").appendChild(newChild);
      |            };
      |
      |            exampleSocket.onopen = function(event) {
      |                exampleSocket.send("socket seems to be open...");
      |            };
      |
      |            exampleSocket.send("socket says: hello, server!!");
      |        </script>
      |    </head>
      |    <body>
      |        Starting websocket...
      |        <div id = "1">
      |
      |        </div>
      |    </body>
      |</html>
      |""".stripMargin

  def websocketFlow: Flow[Message, Message, Any] = Flow[Message].map {
    case tm: TextMessage =>
      TextMessage(
        Source.single("Server says back:") ++ tm.textStream ++ Source.single(
          "!"
        )
      )
    case bm: BinaryMessage => {
      bm.dataStream.runWith(Sink.ignore)
      TextMessage(Source.single("server received a binary message..."))
    }
  }
  val websocketRoute = (pathEndOrSingleSlash & get) {
    complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
  } ~ path("greeter") {
    handleWebSocketMessages(socialFlow)
  }

  Http().newServerAt("localhost", 8080).bind(websocketRoute)

  case class SocialPost(owner: String, content: String)
  val socialFeed = Source(
    List(
      SocialPost("Martin", "Scala 3 is announced"),
      SocialPost("Martin", "I killed Java.")
    )
  )

  val socialMessages = socialFeed
    .throttle(1, 1.second)
    .map(socialPost =>
      TextMessage(s"${socialPost.owner} said ${socialPost.content}")
    )
  val socialFlow: Flow[Message, Message, Any] = Flow.fromSinkAndSource(
    Sink.foreach[Message](println),
    socialMessages
  )
}
