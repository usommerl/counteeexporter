package app

import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import cats.~>
import dev.usommerl.BuildInfo
import io.odin._
import org.http4s.Uri
import org.http4s.client.middleware.{RequestLogger, ResponseLogger}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.{middleware, Server}

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    app.config.resource[IO].flatMap(runF[IO](_, FunctionK.id)).useForever

  def runF[F[_]: Async](config: Config, functionK: F ~> IO): Resource[F, Unit] =
    for {
      logger <- makeLogger[F](config.logger, functionK)
      _      <- Resource.eval(logger.info(startMessage(BuildInfo)))
      client <- makeClient[F](config.counteeUri)
      _      <- makeServer[F](config.server, client)
    } yield ()

  def makeLogger[F[_]: Async](config: LoggerConfig, functionK: F ~> IO): Resource[F, Logger[F]] =
    Resource
      .pure[F, Logger[F]](consoleLogger[F](config.formatter, config.level))
      .evalTap(logger => Sync[F].delay(OdinInterop.globalLogger.set(logger.mapK(functionK).some)))

  def makeClient[F[_]: Async](uri: Uri): Resource[F, CounteeClient[F]] =
    EmberClientBuilder
      .default[F]
      .withoutUserAgent
      .build
      .map(RequestLogger(logHeaders = false, logBody = false))
      .map(ResponseLogger(logHeaders = false, logBody = false))
      .map(CounteeClient(_, uri))

  def makeServer[F[_]: Async](config: ServerConfig, client: CounteeClient[F]): Resource[F, Server] =
    EmberServerBuilder
      .default[F]
      .withHost(config.host)
      .withPort(config.port)
      .withHttpApp(middleware.Logger.httpApp(logHeaders = false, logBody = false)(Api[F](config.apiDocs, client)))
      .build

  private def startMessage(b: BuildInfo.type): String                                              =
    "BUILD [name: %s, version: %s, vmVersion: %s, scalaVersion: %s, sbtVersion: %s, builtAt: %s]"
      .format(b.name, b.version, System.getProperty("java.vm.version"), b.scalaVersion, b.sbtVersion, b.builtAtString)
}
