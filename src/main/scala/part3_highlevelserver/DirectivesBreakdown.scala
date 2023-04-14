package part3_highlevelserver

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpRequest,
  StatusCodes
}
import akka.http.scaladsl.server.Directives._

object DirectivesBreakdown extends App {
  implicit val system = ActorSystem("DirectivesBreakdown")

  import system.dispatcher

  /*
  type #1: filtering directives
   */

  val simpleHttpMethodRoute = post {
    complete(StatusCodes.Forbidden)
  }

  val simplePathRoute = path("about") {
    complete(
      HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
        |<html>
        | <body>
        |   Hello from world
        | </body>
        |</html>
        |""".stripMargin
      )
    )
  }
  val complexPathRoute = path("api" / "myendpoint") {
    complete(StatusCodes.OK)
  }

  val dontConfuse = path("api/myendpoint") {
    complete(StatusCodes.OK)
  }

  val pathEndRoute = pathEndOrSingleSlash {
    complete(StatusCodes.OK)
  }

  /*
  type #2: extraction directives
   */
  // GET on /api/item/42
  val pathExtractionRoute = path("api" / "item" / IntNumber) {
    (itemNumber: Int) =>
      // other directives
      println(s"I have got: $itemNumber")
      complete(StatusCodes.OK)
  }

  val pathMultiExtractRoute = path("api" / "item" / IntNumber / IntNumber) {
    (id, inventory) =>
      println(s"I have got two numbers in my path: $id, $inventory")
      complete(StatusCodes.OK)
  }

  val queryParamExtractionRoute = path("api" / "item") {
    parameter('id.as[Int]) { (itemId: Int) =>
      println(s"I extraced the id: $itemId")
      complete(StatusCodes.OK)
    }
  }

  val extractRequestRoute = path("controlEndpoint") {
    extractRequest { (httpRequest: HttpRequest) =>
      {
        extractLog { (log: LoggingAdapter) =>
          {
            log.info(s"I got the http request: $httpRequest")
            complete(StatusCodes.OK)
          }
        }
      }
    }
  }
  /*
  Type #3 - composite directives
   */
  val simpleNestedRoute = path("api" / "item") {
    get {
      complete(StatusCodes.OK)
    }
  }
  val compactSimpleNestedRoute = (path("api" / "item") & get) {
    complete(StatusCodes.OK)
  }

  val compactExtractRequestRoute =
    (path("controlEndpoint") & extractRequest & extractLog) {
      (httpRequest, log) =>
        {
          log.info(s"I got the http request: $httpRequest")
          complete(StatusCodes.OK)
        }
    }

  // /about and /aboutUs
  val repeatedRoute = path("about") {
    complete(StatusCodes.OK)
  } ~ path("aboutUs") {
    complete(StatusCodes.OK)
  }

  val dryRoute = (path("about") | path("aboutUs")) {
    complete(StatusCodes.OK)
  }

  // yourblog.com/42 and yourblog.com?postId=42
  val blogById = path(IntNumber) { (blogId: Int) =>
    {
      complete(StatusCodes.OK)
    }
  }

  val blogByQueryParam = parameter('postId.as[Int]) { (blogId: Int) =>
    {
      complete(StatusCodes.OK)
    }
  }

  val combinedBlogByIdRoute = (path(IntNumber) | parameter('postId.as[Int])) {
    (blogId: Int) =>
      complete(StatusCodes.OK)
  }
  /*
  type #4: "actionable" directives
   */
  val completeOkRoute = complete(StatusCodes.OK)
  val failedRoute = path("notSupported") {
    failWith(new RuntimeException("UnSupported!")) // complete with HTTP 500
  }

  val routeWithRejection = path("home") {
    reject
  } ~ path("index") {
    completeOkRoute
  }

  /*
  Exercise:

   */
  val getOrPutPath = path("api" / "myendpoint") {
    get {
      completeOkRoute
    } ~ post {
      complete(StatusCodes.Forbidden)
    }
  }
  Http().newServerAt("localhost", 8080).bind(getOrPutPath)
}
