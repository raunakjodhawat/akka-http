package part3_highlevelserver

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString

import java.io.File
import scala.concurrent.Future
import scala.util.{Failure, Success}

object UploadingFiles extends App {
  implicit val system = ActorSystem("uploading-files")
  import system.dispatcher

  val fileRoute = (pathEndOrSingleSlash & get) {
    complete(
      HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          | <body>
          |   <form action="http://localhost:8080/upload" method="post" enctype="multipart/form-data">
          |     <input type="file" name="myFile">
          |     <button type="submit">upload</button>
          |   </form>
          | </body>
          |</html>
          |""".stripMargin
      )
    )
  } ~ (path("upload") & extractLog) { log =>
    // handle uploading files
    entity(as[Multipart.FormData]) { formdata =>
      // handle file payload
      val partsSource: Source[Multipart.FormData.BodyPart, Any] = formdata.parts
      val filePartSink: Sink[Multipart.FormData.BodyPart, Future[Done]] =
        Sink.foreach[Multipart.FormData.BodyPart] { bodyPart =>
          if (bodyPart.name == "myFile") {
            // create a file
            val filename =
              "src/main/resources/download/" + bodyPart.filename.getOrElse(
                "tempFile" + System.currentTimeMillis()
              )
            val file = new File(filename)
            log.info(s"Writing to file: $filename")

            val fileContentsSource: Source[ByteString, _] =
              bodyPart.entity.dataBytes
            val fileContentsSink: Sink[ByteString, _] =
              FileIO.toPath(file.toPath)

            fileContentsSource.runWith(fileContentsSink)

          }
        }

      val writeOperationFuture = partsSource.runWith(filePartSink)
      onComplete(writeOperationFuture) {
        case Success(_)  => complete("File uploaded.")
        case Failure(ex) => complete(s"File failed to upload: $ex")
      }
    }
  }
  Http().newServerAt("localhost", 8080).bind(fileRoute)

}
