package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.javadsl.server.{MethodRejection, MissingQueryParamRejection}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Rejection, RejectionHandler, Route}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import spray.json._

object HandlingRejections extends App {
  implicit val system = ActorSystem("HandleRejections")
  import system.dispatcher

  val simpleRoute = path("api" / "myEndpoint") {
    get {
      complete(StatusCodes.OK)
    } ~ parameter('id) { _ =>
      complete(StatusCodes.OK)
    }
  }

  // Rejection handlers
  val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers = handleRejections(badRequestHandler) {
    // define server logic inside
    path("api" / "myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~ post {
        handleRejections(forbiddenHandler) {
          parameter('myParam) { _ =>
            complete(StatusCodes.OK)
          }
        }
      }
    }
  }

//  RejectionHandler.default
  implicit val customRejectionHandler = RejectionHandler
    .newBuilder()
    .handle { case m: MethodRejection =>
      println(s"i got a method rejection: ${m}")
      complete("Rejected method")
    }
    .handle { case m: MissingQueryParamRejection =>
      println(s"I got a query param rejection: $m")
      complete("Rejected query param!")
    }
    .result()

  // sealing a route
  Http().newServerAt("localhost", 8080).bind(simpleRoute)
}
