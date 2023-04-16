package part3_highlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
import spray.json._

import java.util.concurrent.TimeUnit
import scala.util.{Success, Failure}

object SecurityDomain extends DefaultJsonProtocol {
  case class LoginRequest(username: String, password: String)

  implicit val loginRequestFormat = jsonFormat2(LoginRequest)

}
object JWTAuthorization extends App with SprayJsonSupport {
  implicit val system = ActorSystem("jwt-authorization")
  import system.dispatcher
  import SecurityDomain._

  val superSecretPasswordDb = Map(
    "admin" -> "admin",
    "raunak" -> "raunak"
  )

  val algorithim = JwtAlgorithm.HS256
  val secretKey = "raunakjodhawatsecret"
  def checkPassword(username: String, password: String): Boolean =
    superSecretPasswordDb.contains(username) && superSecretPasswordDb(
      username
    ) == password
  def createToken(username: String, expirationPeriodInDays: Int): String = {
    val claims = JwtClaim(
      expiration = Some(
        System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(
          expirationPeriodInDays
        )
      ),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("raunakjodhawat.com")
    )
    JwtSprayJson.encode(claims, secretKey, algorithim)
  }
  def isTokenExpired(token: String): Boolean = {
    JwtSprayJson.decode(token, secretKey, Seq(algorithim)) match {
      case Success(claims) =>
        claims.expiration.getOrElse(0L) < System
          .currentTimeMillis() / 1000
      case Failure(_) => true
    }
  }
  def isTokenValid(token: String): Boolean =
    JwtSprayJson.isValid(token, secretKey, Seq(algorithim))

  val loginRoute = post {
    entity(as[LoginRequest]) {
      case LoginRequest(username, password)
          if checkPassword(username, password) =>
        val token = createToken(username, 1)
        respondWithHeader(RawHeader("Access-Token", token)) {
          complete(StatusCodes.OK)
        }

      case _ => complete(StatusCodes.Unauthorized)
    }
  }

  val authenticatedRoute = (path("secureEndpoint") & get) {
    optionalHeaderValueByName("Authorization") {
      case Some(token) =>
        if (isTokenExpired(token)) {
          complete(
            HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = "Token expired."
            )
          )
        } else if (isTokenValid(token)) {
          complete("User accessed authorized endpoint!")
        } else {
          complete(
            HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = "Token is invalid"
            )
          )
        }
      case _ =>
        complete(
          HttpResponse(
            status = StatusCodes.Unauthorized,
            entity = "No token provided"
          )
        )
    }
  }

  val route = loginRoute ~ authenticatedRoute
  Http().newServerAt("localhost", 8080).bind(route)
}
