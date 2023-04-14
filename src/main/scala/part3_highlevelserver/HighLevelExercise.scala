package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import spray.json._

object HighLevelExercise
    extends App
    with SprayJsonSupport
    with DefaultJsonProtocol {
  implicit val system = ActorSystem("HighLevelExercise")
  import system.dispatcher

  case class Person(pin: Int, name: String)

  /*
  - GET /api/people: retrieve all the people you have registered
  - GET /api/people/pin: retrieve the person with that PIN
  - GET /api/people?pin=X (same)
  - POST /api/people with a JSON payload denoting a Person, add that person to your database
   */

  implicit val personDecoder = jsonFormat2(Person)
  var people = List(
    Person(1, "Alice"),
    Person(2, "Bob"),
    Person(3, "Charly")
  )

  val route: Route = pathPrefix("api" / "people") {
    get {
      (path(IntNumber) | parameter('pin.as[Int])) { pin =>
        {
          complete(
            HttpEntity(
              ContentTypes.`application/json`,
              people.find(_.pin == pin).toJson.prettyPrint
            )
          )
        }
      } ~ pathEndOrSingleSlash {
        complete(
          HttpEntity(
            ContentTypes.`application/json`,
            people.toJson.prettyPrint
          )
        )
      }
    } ~ (post & pathEndOrSingleSlash & entity(as[Person])) { (person: Person) =>
      {
        people :+= person
        complete(
          HttpEntity(
            ContentTypes.`application/json`,
            people.toJson.prettyPrint
          )
        )
      }
    }
  }
  Http().newServerAt("localhost", 8080).bind(route)
}
