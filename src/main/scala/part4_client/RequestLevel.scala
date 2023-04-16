package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCodes,
  Uri
}
import akka.stream.scaladsl.{Sink, Source}

import scala.util.{Failure, Success}
import spray.json._

import java.util.UUID

object RequestLevel extends App with PaymentJsonProtocol with SprayJsonSupport {
  implicit val system = ActorSystem("host-level")

  import system.dispatcher

  val responseFuture =
    Http().singleRequest(HttpRequest(uri = "http://www.google.com"))
//
//  responseFuture.onComplete {
//    case Success(response) =>
//      response.discardEntityBytes()
//      println(response)
//    case Failure(ex) =>
//      println(s"request failed $ex")
//  }

  val creditCards = List(
    CreditCard("123-123-123-123", "123", "123123"),
    CreditCard("1234-1234-1234-1234", "123", "123123"),
    CreditCard("165878-123-123-123", "123", "123123")
  )

  import PaymentSystemDomain._

  val paymentRequest = creditCards.map(cc => PaymentRequest(cc, "asdasd", 99))
  val serverHttpRequests = paymentRequest.map(paymentRequest => {
    HttpRequest(
      HttpMethods.POST,
      uri = "http://localhost:8080/api/payments",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  })
  Source(serverHttpRequests)
    .mapAsyncUnordered(10)(request => Http().singleRequest(request))
    .runForeach(println)
}
