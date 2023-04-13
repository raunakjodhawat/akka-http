package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}

object HighLevelIntro extends App {
  implicit val system = ActorSystem("HighLevelIntro")

  import system.dispatcher

  // directives
  import akka.http.scaladsl.server.Directives._
  val simpleRoute: Route = path("home") { // directive
    complete(StatusCodes.OK) // directive
  }

  val pathGetRoute: Route = path("home") {
    get {
      complete(StatusCodes.OK)
    }
  }

  // chaining directives with ~
  val chainedRoute: Route = path("my") {
    get {
      complete(StatusCodes.OK)
    } ~ post {
      complete(StatusCodes.Forbidden)
    }
  } ~ path("home") {
    complete(
      HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          | <body>
          |   Hello World
          | </body>
          |</html>
          |""".stripMargin
      )
    )
  } // routing tree
  Http().newServerAt("localhost", 8080).bind(chainedRoute)

}
