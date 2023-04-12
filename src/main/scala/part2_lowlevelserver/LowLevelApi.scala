package part2_lowlevelserver

import akka.actor.ActorSystem
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

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object LowLevelApi extends App {
  implicit val system = ActorSystem("LowLevelServerAPI")
  import system.dispatcher

  val serverSource = Http().bind("localhost", 8080)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from: ${connection.remoteAddress}")
  }

//  val serverBindingFuture = serverSource.to(connectionSink).run()
//  serverBindingFuture.onComplete {
//    case Success(binding) => {
//      println(s"Server binding successful.")
//      binding.terminate(2.seconds)
//    }
//    case Failure(ex) => println(s"Server binding failed: $ex")
//  }

  /*
  Method 1: synchronously serve HTTP responses
   */

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Hello from Akka Http!
            |</body>
            |</html>
            |""".stripMargin
        )
      )
    case request: HttpRequest => {
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Oops content not found!
            |</body>
            |</html>
          |""".stripMargin
        )
      )
    }
  }
  val httpSinkConnectionHandler = Sink.foreach[IncomingConnection] {
    connection =>
      connection.handleWithSyncHandler(requestHandler)
  }

//  Http().bind("localhost", 8080).runWith(httpSinkConnectionHandler)
//  Http().bindAndHandleSync(requestHandler, "localhost", 8080)

  /*
  Method 2: serve back http responses asynchronously
   */

  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(
          HttpMethods.GET,
          Uri.Path("/home"),
          _,
          _,
          _
        ) => // method, URI, HTTP headers, content and the protocol (Http1.1/Http2.0)
      Future(
        HttpResponse(
          StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
            |<html>
            | <body>
            |   Hello from Akka Http!
            | </body>
            |</html>
            |""".stripMargin
          )
        )
      )
    case request: HttpRequest => {
      request.discardEntityBytes()
      Future(
        HttpResponse(
          StatusCodes.NotFound,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
            |<html>
            | <body>
            |   Oops content not found!
            | </body>
            |</html>
            |""".stripMargin
          )
        )
      )
    }
  }

//  Http().newServerAt("localhost", 8080).bind(asyncRequestHandler)

  /*
  Method 3: async via akka streams
   */

  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] =
    Flow[HttpRequest].map {
      case HttpRequest(
            HttpMethods.GET,
            Uri.Path("/home"),
            _,
            _,
            _
          ) => // method, URI, HTTP headers, content and the protocol (Http1.1/Http2.0)
        HttpResponse(
          StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   Hello from Akka Http!
              | </body>
              |</html>
              |""".stripMargin
          )
        )
      case request: HttpRequest => {
        request.discardEntityBytes()
        HttpResponse(
          StatusCodes.NotFound,
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
              |<html>
              | <body>
              |   Oops content not found!
              | </body>
              |</html>
              |""".stripMargin
          )
        )
      }
    }

  // Http().newServerAt("localhost", 8080).bindFlow(streamsBasedRequestHandler)

  /*
  Exercise: Create your own HTTP server running on localhost on 8080, which replies
  - with a welcome message on the "front door" /
  - with a proper HTML on /about
  - with a 404 message otherwise
   */

  val responseHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
      Future(
        HttpResponse(
          entity = HttpEntity(
            ContentTypes.`text/html(UTF-8)`,
            """
            |<html>
            | <body>
            |   Welcome to my Page!
            | </body>
            |</html>
            |""".stripMargin
          )
        )
      )
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      Future(
        HttpResponse(
          entity = HttpEntity(
            ContentTypes.`text/plain(UTF-8)`,
            "Welcome to the Jungle"
          )
        )
      )
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      Future(
        HttpResponse(
          StatusCodes.Found,
          headers = List(Location("http://google.com"))
        )
      )
    case request: HttpRequest => {
      request.discardEntityBytes()
      Future(
        HttpResponse(
          StatusCodes.NotFound,
          entity = HttpEntity(
            ContentTypes.`text/plain(UTF-8)`,
            "Oops"
          )
        )
      )
    }
  }
  val bindingFuture =
    Http().newServerAt("localhost", 8080).bind(responseHandler)

  // shutdown the server:
  bindingFuture
    .flatMap(binding => binding.unbind())
    .onComplete(_ => system.terminate())
}
