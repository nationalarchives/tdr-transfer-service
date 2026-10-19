package uk.gov.nationalarchives.tdr.transfer.service.api

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits.toSemigroupKOps
import com.comcast.ip4s.{IpLiteralSyntax, Port}
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{Logger, Throttle}
import org.http4s.server.Server
import org.http4s.{HttpApp, HttpRoutes}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.apispec.openapi.Info
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import uk.gov.nationalarchives.tdr.keycloak.TdrKeycloakDeployment
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.Tls
import uk.gov.nationalarchives.tdr.transfer.service.api.controllers.{LoadController, TransferErrorsController}

import java.nio.file.{Files, Paths}
import java.security.{KeyFactory, KeyStore}
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

object TransferServiceServer extends IOApp {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  private val appConfig = ApplicationConfig.appConfig
  private val authUrl = appConfig.auth.url
  private val realm = appConfig.auth.realm
  private val serviceConfig = appConfig.transferServiceApi

  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  implicit val keycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment(s"$authUrl", realm, 8080)

  private val apiPort: Port = Port.fromInt(serviceConfig.port).getOrElse(port"8080")

  private val infoTitle = "TDR Transfer Service API"
  private val infoVersion = "0.0.3"
  private val infoDescription = Some("APIs to allow client services to transfer records to TDR")

  private val openApiInfo: Info = Info(infoTitle, infoVersion, description = infoDescription)
  private val loadController = LoadController()
  private val transferErrorsController = TransferErrorsController()

  private val documentationEndpoints =
    SwaggerInterpreter().fromEndpoints[IO](loadController.endpoints ++ transferErrorsController.endpoints, openApiInfo)

  val healthCheckRoute: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root / "healthcheck" =>
    Ok("Healthy")
  }

  private val featureAccessBlocks = appConfig.featureAccessBlocks

  val allRoutes = featureAccessBlocks match {
    case _ if featureAccessBlocks.blockServiceEndpoints => healthCheckRoute
    case _ if featureAccessBlocks.blockApiDocumentation =>
      loadController.routes <+> transferErrorsController.routes <+> healthCheckRoute
    case _ =>
      Http4sServerInterpreter[IO]().toRoutes(documentationEndpoints) <+> loadController.routes <+>
        transferErrorsController.routes <+> healthCheckRoute
  }

  private def throttleService(service: HttpApp[IO]): IO[HttpApp[IO]] = Throttle.httpApp[IO](
    amount = appConfig.transferServiceApi.throttleAmount,
    per = appConfig.transferServiceApi.throttlePerMs.milliseconds
  )(service)

  private val logAction: String => IO[Unit] =
    (msg: String) => {
      if (msg.contains("GET /healthcheck") || msg.contains("200 OK")) {
        IO.unit
      } else logger.info(msg)
    }

  private val transferServiceServer = for {
    httpApp <- Resource.eval(throttleService(allRoutes.orNotFound))
    finalApp = Logger.httpApp(logHeaders = serviceConfig.logHeaders, logBody = serviceConfig.logBody, logAction = Some(logAction))(httpApp)
    server <- serverResource(finalApp, apiPort, serviceConfig.tls)
  } yield server

  private def tlsContext(config: Tls): IO[TLSContext[IO]] =
    Resource.fromAutoCloseable(IO.blocking(Files.newInputStream(Paths.get(config.certificatePath)))).use { input =>
      IO.blocking {
        val certificates = CertificateFactory.getInstance("X.509").generateCertificates(input).asScala.toArray[java.security.cert.Certificate]
        require(certificates.nonEmpty, "TLS certificate file must contain at least one X.509 certificate")
        val keyPem = Files.readString(Paths.get(config.privateKeyPath))
        require(keyPem.contains("-----BEGIN PRIVATE KEY-----"), "TLS private key must be an unencrypted PKCS#8 PEM key")
        val keyBytes = Base64.getDecoder.decode(
          keyPem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "")
        )
        val privateKey = KeyFactory.getInstance(certificates.head.getPublicKey.getAlgorithm).generatePrivate(new PKCS8EncodedKeySpec(keyBytes))
        val keyStore = KeyStore.getInstance("PKCS12")
        val password = Array.emptyCharArray
        keyStore.load(null, password)
        keyStore.setKeyEntry("transfer-service", privateKey, password, certificates)
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        keyManagerFactory.init(keyStore, password)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagerFactory.getKeyManagers, null, null)
        Network[IO].tlsContext.fromSSLContext(sslContext)
      }
    }

  private[api] def serverResource(httpApp: HttpApp[IO], port: Port, tls: Tls): Resource[IO, Server] =
    Resource.eval(tlsContext(tls)).flatMap { context =>
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port)
        .withHttpApp(httpApp)
        .withTLS(context)
        .build
    }

  override def run(args: List[String]): IO[ExitCode] = {
    transferServiceServer.use(_ => IO.never).as(ExitCode.Success)
  }
}
