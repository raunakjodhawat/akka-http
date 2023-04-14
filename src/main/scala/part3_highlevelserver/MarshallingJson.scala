package part3_highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
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

case class Player(nickname: String, characterClass: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPLayersByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
  case object OperationSuccess
}

class GameAreaMap extends Actor with ActorLogging {
  import GameAreaMap._
  var players = Map[String, Player]()

  override def receive: Receive = {
    case GetAllPlayers => {
      log.info("getting all players")
      sender() ! players.values.toList
    }
    case GetPlayer(nickname) => {
      log.info(s"Getting player by $nickname")
      sender() ! players.get(nickname)
    }
    case GetPLayersByClass(characterClass) => {
      log.info(s"Getting player by character class: $characterClass")
      sender() ! players.values.toList.filter(
        _.characterClass == characterClass
      )
    }
    case AddPlayer(player) => {
      log.info(s"trying to add player: $player")
      players = players + (player.nickname -> player)
      sender() ! OperationSuccess
    }
    case RemovePlayer(player) => {
      log.info(s"trying to remove player: $player")
      players = players - player.nickname
      sender() ! OperationSuccess
    }
  }
}
object MarshallingJson extends App {
  implicit val system = ActorSystem("HighLevelExample")

  import system.dispatcher
  import GameAreaMap._
  val rtjvmGameMap = system.actorOf(Props[GameAreaMap], "rockthejvmGameAreaMap")
  val playerList = List(
    Player("raunak", "1", 90),
    Player("jodhawat", "1", 100),
    Player("Priyal", "2", 54),
    Player("rajiv", "2", 55),
    Player("lodaria", "2", 56)
  )
  playerList.foreach { player =>
    rtjvmGameMap ! AddPlayer(player)
  }

  /*
  GET /api/player => returns all the players in the map, as json
  GET /api/player/(nickname)
  GET /api/player?nickname=X
  GET /api/player/class/(charClass)
  POST /api/player
  DELETE /api/player
   */

  val rtjvmGameRouteSkel = pathPrefix("api" / "player") {
    get {
      path("class" / Segment) { characterclass =>
        {
          reject
        }
      } ~ (path(Segment) | parameter('nickname)) { nickname =>
        {
          reject
        }
      } ~ pathEndOrSingleSlash {
        reject
      }
    } ~ post {
      reject
    } ~ delete {
      reject
    }
  }
}
