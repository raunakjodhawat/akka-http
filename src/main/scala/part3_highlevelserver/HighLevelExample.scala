package part3_highlevelserver

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import spray.json._

object HighLevelExample extends App with GuitarStoreJsonProtocol {
  implicit val system = ActorSystem("HighLevelExample")
  import system.dispatcher

  /*
  GET /api/guitar fetches all the guitars in the store
   */
  import GuitarDB._
  val guitarDb = system.actorOf(Props[GuitarDB], "lowLevelGuitarDB")
  val guitarList = List(
    Guitar("1", "2"),
    Guitar("3", "4"),
    Guitar("5", "6")
  )

  guitarList.foreach { guitar =>
    guitarDb ! CreateGuitar(guitar)
  }

  implicit val timeout = Timeout(2.seconds)
  val guitarServerRoute = path("api" / "guitar") {
    (parameter('id.as[Int]) | path(IntNumber)) { (id: Int) =>
      get {
        val guitarFuture: Future[Option[Guitar]] =
          (guitarDb ? FindGuitar(id)).mapTo[Option[Guitar]]
        val entity = guitarFuture.map { guitar =>
          HttpEntity(
            ContentTypes.`application/json`,
            guitar.toJson.prettyPrint
          )
        }
        complete(entity)
      }
    } ~ get {
      val guitarsFuture: Future[List[Guitar]] =
        (guitarDb ? FindAllGuitars).mapTo[List[Guitar]]
      val entityFuture = guitarsFuture.map { guitars =>
        HttpEntity(
          ContentTypes.`application/json`,
          guitars.toJson.prettyPrint
        )
      }
      complete(entityFuture)
    } ~ path("api" / "guitar" / "inventory") {
      get {
        parameter('inStock.as[Boolean]) { inStock =>
          {
            val guitarsFuture: Future[List[Guitar]] =
              (guitarDb ? FindAllGuitarsInStock).mapTo[List[Guitar]]
            val entityFuture = guitarsFuture.map { guitars =>
              HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              )
            }
            complete(entityFuture)
          }
        }
      }
    }
  }

  def toHttpEntity(payload: String) =
    HttpEntity(ContentTypes.`application/json`, payload)
  val simplifiedGuitarServerRoute = (pathPrefix("api" / "guitar") & get) {
    path("inventory") {
      parameter('inStock.as[Boolean]) { inStock =>
        {
          complete(
            (guitarDb ? (if (inStock) FindAllGuitarsInStock
                         else FindAllGuitarsOutOfStock))
              .mapTo[List[Guitar]]
              .map(_.toJson.prettyPrint)
              .map(toHttpEntity)
          )
        }
      }
    } ~ (path(IntNumber) | parameter('id.as[Int])) { guitarId =>
      {
        complete(
          (guitarDb ? FindGuitar(guitarId))
            .mapTo[Option[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity)
        )
      }
    } ~ pathEndOrSingleSlash {
      complete(
        (guitarDb ? FindAllGuitars)
          .mapTo[List[Guitar]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity)
      )
    }
  }
  Http().newServerAt("localhost", 8080).bind(simplifiedGuitarServerRoute)
}
