package app

import cats.effect._
import cats.implicits._
import dev.usommerl.BuildInfo
import io.odin._
import eu.timepit.refined.auto._
import org.http4s.client.middleware.{RequestLogger, ResponseLogger}
import org.http4s.server.middleware
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.Uri

object Main extends IOApp.Simple {

  def run: IO[Unit] = resources[IO].use(_ => IO.never)

  def resources[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Unit] =
    for {
      config <- app.config.resource
      logger <- loggerResource[F](config.logger)
      _      <- Resource.eval(logger.info(startMessage(BuildInfo)))
      client <- counteeClientResource[F](config.counteeUri)
      _      <- serverResource[F](config.server, client)
    } yield ()

  def loggerResource[F[_]: ConcurrentEffect: Timer](config: LoggerConfig): Resource[F, Logger[F]] =
    Resource
      .pure[F, Logger[F]](consoleLogger[F](config.formatter, config.level))
      .evalTap(logger => Sync[F].delay(OdinInterop.globalLogger.set(logger.mapK(Effect.toIOK).some)))

  def counteeClientResource[F[_]: Concurrent: Timer: ContextShift](uri: Uri): Resource[F, CounteeClient[F]] =
    EmberClientBuilder
      .default[F]
      .withoutUserAgent
      .build
      .map(RequestLogger(logHeaders = false, logBody = false))
      .map(ResponseLogger(logHeaders = false, logBody = false))
      .map(CounteeClient(_, uri))

  def serverResource[F[_]: Concurrent: Timer: ContextShift](config: ServerConfig, client: CounteeClient[F]): Resource[F, Server[F]] =
    EmberServerBuilder
      .default[F]
      .withHost("0.0.0.0")
      .withPort(config.port)
      .withHttpApp(middleware.Logger.httpApp(logHeaders = false, logBody = false)(Api[F](config.apiDocs, client)))
      .build

  private def startMessage(b: BuildInfo.type): String =
    "BUILD [name: %s, version: %s, vmVersion: %s, scalaVersion: %s, sbtVersion: %s, builtAt: %s]"
      .format(b.name, b.version, System.getProperty("java.vm.version"), b.scalaVersion, b.sbtVersion, b.builtAtString)
}
