package uk.gov.nationalarchives.tdr.transfer.service

import cats.effect.{IO, Resource}
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.Tls

import java.nio.file.{Files, Paths}
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import scala.jdk.CollectionConverters._

class TransportLayerSecurity(config: Tls) {
  def context: IO[TLSContext[IO]] =
    Resource.fromAutoCloseable(IO.blocking(Files.newInputStream(Paths.get(config.keyStorePath)))).use { input =>
      IO.blocking {
        val keyStore = KeyStore.getInstance("PKCS12")
        val password = Array.emptyCharArray
        keyStore.load(input, password)
        require(
          keyStore.aliases().asScala.exists(alias => keyStore.isKeyEntry(alias) && Option(keyStore.getCertificateChain(alias)).exists(_.nonEmpty)),
          "TLS key store must contain a private key and certificate chain"
        )
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        keyManagerFactory.init(keyStore, password)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagerFactory.getKeyManagers, null, null)
        Network[IO].tlsContext.fromSSLContext(sslContext)
      }
    }
}

object TransportLayerSecurity {
  val config: ApplicationConfig.Configuration = ApplicationConfig.appConfig
  def apply() = new TransportLayerSecurity(config.transferServiceApi.tls)
}
