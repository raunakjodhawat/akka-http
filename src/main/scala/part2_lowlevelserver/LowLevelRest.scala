package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCodes,
  Uri
}
import akka.stream.scaladsl.{Flow, Sink}
import spray.json._
import scala.concurrent.Future

case class Guitar(make: String, model: String)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
}

class GuitarDB extends Actor with ActorLogging {
  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars => {
      log.info("Searching for all guitars")
      sender() ! guitars.values.toList
    }
    case FindGuitar(id: Int) => {
      log.info(s"Searching Guitar by id: $id")
      sender() ! guitars.get(id)
    }
    case CreateGuitar(guitar: Guitar) => {
      log.info(s"Adding guitar $guitar with id: $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
    }
  }
}
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat = jsonFormat2(Guitar)
}
object LowLevelRest extends App with GuitarStoreJsonProtocol {
  import system.dispatcher
  implicit val system = ActorSystem("LowLevelRest")

  /*
  GET: /api/guitar => all guitars in the store
  POST: /api/guitar => insert the guitar into the store

   */

  // JSON serialize (marshalling -> serializing)
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  // unmarshalling
  val simpleGuitarJsonString =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster"
      |}
      |""".stripMargin

  println(simpleGuitarJsonString.parseJson.convertTo[Guitar])
}
