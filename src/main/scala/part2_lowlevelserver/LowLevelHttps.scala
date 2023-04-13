package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.Uri.Query
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
import part2_lowlevelserver.GuitarDB.{
  CreateGuitar,
  FindAllGuitars,
  GuitarCreated,
  GuitarQuantityUpdate,
  UpdateGuitarQuantity
}
import spray.json._
import akka.pattern.ask
import akka.util.Timeout
import part2_lowlevelserver.LowLevelHttps.getClass

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object HttpsContext {

  // Step1: key store
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keyStoreFile: InputStream =
    getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  // new FileInputStream(new File("src/main/resources/keystore.pkcs12"))
  val password =
    "akka-https".toCharArray // fetch the password from a secure place!
  ks.load(keyStoreFile, password)

  // step 2: initialize a key manager
  val keyManagerFactor =
    KeyManagerFactory.getInstance("SunX509") // PKI = public key infrastructure
  keyManagerFactor.init(ks, password)

  // step 3: initialize a trust manager
  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // step 4: initialize an ssl context
  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(
    keyManagerFactor.getKeyManagers,
    trustManagerFactory.getTrustManagers,
    new SecureRandom
  )

  // step 5: return the https connection context
  val httpsConnectionContext: HttpsConnectionContext =
    ConnectionContext.https(sslContext)

}
object LowLevelHttps extends App {
  import system.dispatcher
  implicit val system = ActorSystem("LowLevelHttps")

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

  val httpsBinding = Http()
    .newServerAt("localhost", 8443)
    .enableHttps(HttpsContext.httpsConnectionContext)
    .bindSync(requestHandler)
}
