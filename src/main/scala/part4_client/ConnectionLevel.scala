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
  Uri
}
import akka.stream.scaladsl.{Sink, Source}

import scala.util.{Failure, Success}
import spray.json._

object ConnectionLevel
    extends App
    with PaymentJsonProtocol
    with SprayJsonSupport {
  implicit val system = ActorSystem("connection-level")
  import system.dispatcher

  val connectionFlow = Http().outgoingConnection("www.google.com")

  def oneOffRequest(request: HttpRequest) =
    Source.single(request).via(connectionFlow).runWith(Sink.head)

//  oneOffRequest(HttpRequest()).onComplete {
//    case Success(value) => println(s"got successful response, $value")
//    case Failure(ex)    => println(s"sending the request failed: $ex")
//  }

  /*
  Payment system
   */

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
      uri = Uri("/api/payments"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  })

  Source(serverHttpRequests)
    .via(Http().outgoingConnection("localhost", 8080))
    .to(Sink.foreach[HttpResponse](println))
    .run()
}
