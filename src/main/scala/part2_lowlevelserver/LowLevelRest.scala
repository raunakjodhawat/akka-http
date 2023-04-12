package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
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
import part2_lowlevelserver.GuitarDB.{
  CreateGuitar,
  FindAllGuitars,
  GuitarCreated
}
import spray.json._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

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
  /*
  setup - create actor
   */
  val guitarDb = system.actorOf(Props[GuitarDB], "lowLevelGuitarDB")
  val guitarList = List(
    Guitar("1", "2"),
    Guitar("3", "4"),
    Guitar("5", "6")
  )

  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }
  /*
  Server code
   */
  implicit val defaultTimeout = Timeout(2.seconds)
  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/api/guitar"), _, _, _) => {
      val guitarsFuture: Future[List[Guitar]] =
        (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
      guitarsFuture.map { guitars =>
        HttpResponse(
          entity = HttpEntity(
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          )
        )
      }
    }
    case HttpRequest(
          HttpMethods.POST,
          Uri.Path("/api/guitar"),
          _,
          entity,
          _
        ) => {
      // entities are a Source[ByteString]
      val strictEntityFuture = entity.toStrict(3.seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]
        val guitarCreatedFuture: Future[GuitarCreated] =
          (guitarDb ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }
    }
    case request: HttpRequest => {
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
    }
  }

  Http().newServerAt("localhost", 8080).bind(requestHandler)
}
