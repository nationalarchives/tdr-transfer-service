package uk.gov.nationalarchives.tdr.transfer.service

import cats.effect.{IO, Resource}
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import uk.gov.nationalarchives.tdr.transfer.service.ApplicationConfig.Tls

import java.nio.file.{Files, Paths}
import java.security.{KeyFactory, KeyStore}
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import scala.jdk.CollectionConverters._

class TransportLayerSecurity(config: Tls) {
  def context: IO[TLSContext[IO]] =
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
}

object TransportLayerSecurity {
  val config: ApplicationConfig.Configuration = ApplicationConfig.appConfig
  def apply() = new TransportLayerSecurity(config.transferServiceApi.tls)
}
