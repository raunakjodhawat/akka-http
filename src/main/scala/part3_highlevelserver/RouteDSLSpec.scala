package part3_highlevelserver

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
case class Book(id: Int, author: String, title: String)

trait BookJsonProtocol extends DefaultJsonProtocol {
  implicit val bookFormat = jsonFormat3(Book)
}

class RouteDSLSpec
    extends AnyWordSpecLike
    with should.Matchers
    with ScalatestRouteTest
    with BookJsonProtocol {
  import RouteDSLSpec._

  "A digital library" when {
    "queried for all books" should {
      "return all the books in the library" in {
        Get("/api/book") ~> libraryRoute ~> check {
          // assertions
          status shouldBe StatusCodes.OK
          entityAs[List[Book]] shouldBe books
        }
      }
    }
    "queried for a single book" should {
      "return a single book" in {
        Get("/api/book?id=2") ~> libraryRoute ~> check {
          status shouldBe StatusCodes.OK
          responseAs[Option[Book]] shouldBe Some(books(1))
        }
      }
    }
    "something" should {
      "return a book by calling the endpoint with id in the path" in {
        Get("/api/book/2") ~> libraryRoute ~> check {
          response.status shouldBe StatusCodes.OK

          val strictEntityFuture = response.entity.toStrict(1.second)
          val strictEntity = Await.result(strictEntityFuture, 1.second)

          strictEntity.contentType shouldBe ContentTypes.`application/json`
          val book =
            strictEntity.data.utf8String.parseJson.convertTo[Option[Book]]
          book shouldBe Some(books(1))
        }
      }
    }

    "something else" should {
      "insert a book into the db" in {
        val newBook = Book(4, "2", "6")
        Post("/api/book", newBook) ~> libraryRoute ~> check {
          status shouldBe StatusCodes.OK

          books should contain(newBook)
        }
      }
    }

    "something else else" should {
      "not accept other methods than post and get" in {
        Delete("/api/book") ~> libraryRoute ~> check {
          rejections should not be empty

          val methodRejections = rejections.collect {
            case rejection: MethodRejection => rejection
          }
          methodRejections.length shouldBe 2
        }
      }
    }

    "get books by author name" should {
      "return all the books of a author" in {
        Get("/api/book/author/1") ~> libraryRoute ~> check {
          status shouldBe StatusCodes.OK
          entityAs[List[Book]] shouldBe List(books(0), books(1))
        }
      }
    }
  }
}

object RouteDSLSpec extends BookJsonProtocol with SprayJsonSupport {

  // code under test
  var books = List(
    Book(1, "1", "1"),
    Book(2, "1", "2"),
    Book(3, "2", "3")
  )

  /*
  GET /api/book - all books in librarys
  GET /api/book/X /api/book?id=X - return a single book with id x
  POST /api/book - adds a new book to the library
  GET /api/book/author/X - return all the books from the actor X
   */
  val libraryRoute = pathPrefix("api" / "book") {
    (path("author" / Segment) & get) { author =>
      complete(books.filter(_.author == author))
    } ~
      get {
        (path(IntNumber) | parameter('id.as[Int])) { id =>
          complete(books.find(_.id == id))
        } ~ pathEndOrSingleSlash {
          complete(books)
        }
      } ~ post {
        entity(as[Book]) { book =>
          books = books :+ book
          complete(StatusCodes.OK)
        }
      }

  }
}
