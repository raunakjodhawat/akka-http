package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.Uri.Query
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
  GuitarCreated,
  GuitarQuantityUpdate,
  UpdateGuitarQuantity
}
import spray.json._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

case class Guitar(make: String, model: String, quantity: Int = 0)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
  case object FindAllGuitarsInStock

  case object FindAllGuitarsOutOfStock

  case class UpdateGuitarQuantity(id: Int, quantity: Int)

  trait GuitarQuantityUpdate {
    val updateSuccess: Boolean
  }
  case object UpdateSuccess extends GuitarQuantityUpdate {
    override val updateSuccess: Boolean = true
  }
  case object UpdateFailure extends GuitarQuantityUpdate {
    override val updateSuccess: Boolean = false
  }
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
    case FindAllGuitarsInStock => {
      log.info(s"Finding all guitars in stock")
      sender() ! guitars.values.filter(_.quantity > 0)
    }
    case FindAllGuitarsOutOfStock => {
      log.info(s"Finding all guitars not in stock")
      sender() ! guitars.values.filter(_.quantity <= 0)
    }
    case UpdateGuitarQuantity(id, quantity) => {
      log.info("Updating guitar quantity")
      val guitarFromDb = guitars.get(id)
      guitarFromDb match {
        case Some(guitar) => {
          guitars =
            guitars + (id -> Guitar(guitar.make, guitar.model, quantity))
          sender() ! UpdateSuccess
        }
        case None => {
          sender() ! UpdateFailure
        }
      }
    }
  }
}
trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat = jsonFormat3(Guitar)
}
object LowLevelRest extends App with GuitarStoreJsonProtocol {
  import system.dispatcher
  implicit val system = ActorSystem("LowLevelRest")

  /*
  GET: /api/guitar => all guitars in the store
  GET: /api/guitar?id=x => fetches the guitar associated with id x
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
      |  "model": "Stratocaster",
      |  "quantity": 1
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

  import GuitarDB.{FindGuitar, FindAllGuitarsInStock, FindAllGuitarsOutOfStock}
  implicit val defaultTimeout = Timeout(2.seconds)
  def getGuitar(query: Query): Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt) // Option[Int]
    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) => {
        val guitarFuture: Future[Option[Guitar]] =
          (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
        guitarFuture.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )
        }
      }
    }
  }

  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(
          HttpMethods.GET,
          uri @ Uri.Path("/api/guitar"),
          _,
          _,
          _
        ) => {
      val query = uri.query() // query object <=> Map[String, String]
      if (query.isEmpty) {
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
      } else {
        getGuitar(query)
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
    case HttpRequest(
          HttpMethods.GET,
          uri @ Uri.Path("/api/guitar/inventory"),
          _,
          _,
          _
        ) => {
      val queryParam = uri.query()
      val inStockQueryParam = queryParam.get("inStock").map(_.toBoolean)
      inStockQueryParam match {
        case Some(true) => {
          val guitarInStock: Future[List[Guitar]] =
            (guitarDb ? FindAllGuitarsInStock).mapTo[List[Guitar]]
          guitarInStock.map { guitars =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            )
          }
        }
        case Some(false) => {
          val guitarInStock: Future[List[Guitar]] =
            (guitarDb ? FindAllGuitarsOutOfStock).mapTo[List[Guitar]]
          guitarInStock.map { guitars =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            )
          }
        }
        case None => Future(HttpResponse(StatusCodes.NotFound))
      }
    }
    case HttpRequest(
          HttpMethods.POST,
          uri @ Uri.Path("/api/guitar/inventory"),
          _,
          _,
          _
        ) => {
      val query = uri.query()
      val mayBeId = query.get("id").map(_.toInt)
      val mayBeQuantity = query.get("quantity").map(_.toInt)

      (mayBeId, mayBeQuantity) match {
        case (Some(id: Int), Some(quantity: Int)) => {
          val updateStatusFuture =
            (guitarDb ? UpdateGuitarQuantity(id, quantity))
              .mapTo[GuitarQuantityUpdate]

          updateStatusFuture.map { updateStatus =>
            if (updateStatus.updateSuccess) {
              HttpResponse(StatusCodes.Created)
            } else {
              HttpResponse(StatusCodes.BadRequest)
            }
          }
        }
        case _ => Future(HttpResponse(StatusCodes.BadRequest))
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

  /*
  Exercise: enhance the guitar case class with a quantity field, by default 0
  - GET /api/guitar/inventory?inStock=true/false
  - POST to /api/guitar/inventory?id=X&quantity=Y adds Y guitars to the stock for guitar for id x
   */
}
