package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.javadsl.server.{MethodRejection, MissingQueryParamRejection}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{
  ExceptionHandler,
  Rejection,
  RejectionHandler,
  Route
}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import spray.json._

object HandlingExceptions extends App {
  implicit val system = ActorSystem("HandleRejections")

  import system.dispatcher

  val simpleRoute = path("api" / "people") {
    get {
      throw new RuntimeException("Getting all people")
    } ~ post {
      parameter('id) { id =>
        if (id.length > 2)
          throw new NoSuchElementException(s"Paramaeter $id not found")
        complete(StatusCodes.OK)
      }
    }
  }

  implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException => complete(StatusCodes.NotFound, e.getMessage)
    case e: IllegalArgumentException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  val runtimeExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  val noSuchElementException: ExceptionHandler = ExceptionHandler {
    case e: NoSuchElementException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  val simpleRoutev2 = handleExceptions(runtimeExceptionHandler) {
    path("api" / "people") {
      get {
        throw new RuntimeException("Getting all people")
      } ~ handleExceptions(noSuchElementException) {
        post {
          parameter('id) { id =>
            if (id.length > 2)
              throw new NoSuchElementException(s"Paramaeter $id not found")
            complete(StatusCodes.OK)
          }
        }
      }
    }
  }
  Http().newServerAt("localhost", 8080).bind(Route.seal(simpleRoutev2))
}
