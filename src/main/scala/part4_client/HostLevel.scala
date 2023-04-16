package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
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

object HostLevel extends App with PaymentJsonProtocol {
  implicit val system = ActorSystem("host-level")
  import system.dispatcher

  val poolFlow = Http().cachedHostConnectionPool[Int]("www.google.com")

//  Source(1 to 10)
//    .map(i => (HttpRequest(), i))
//    .via(poolFlow)
//    .map {
//      case (Success(response), value) =>
//        response.discardEntityBytes()
//        s"Request: $value has received response: $response"
//      case (Failure(ex), value) =>
//        s"Request: $value has failed: $ex"
//    }
//    .runWith(Sink.foreach[String](println))

  import PaymentSystemDomain._
  val creditCards = List(
    CreditCard("123-123-123-123", "123", "123123"),
    CreditCard("1234-1234-1234-1234", "123", "123123"),
    CreditCard("165878-123-123-123", "123", "123123")
  )

  val paymentRequest = creditCards.map(cc => PaymentRequest(cc, "asdasd", 99))
  val serverHttpRequests = paymentRequest.map(paymentRequest => {
    (
      HttpRequest(
        HttpMethods.POST,
        uri = Uri("/api/payments"),
        entity = HttpEntity(
          ContentTypes.`application/json`,
          paymentRequest.toJson.prettyPrint
        )
      ),
      UUID.randomUUID().toString
    )
  })

  Source(serverHttpRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach { // (Try[HttpResponse], String)
      case (
            Success(response @ HttpResponse(StatusCodes.Forbidden, _, _, _)),
            orderId
          ) =>
        println(s"$orderId was not allowed to proceed")
      case (Success(response), orderId) =>
        println(
          s"the orderID, $orderId was succesfull and returned the response: $response"
        )
      case (Failure(ex), orderId) =>
        println(s"The order Id: $orderId could not be completed: $ex")
    }

  // high-volume and low latency requests
}
