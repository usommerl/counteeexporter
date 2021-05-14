package app

import cats.implicits._
import ciris._
import ciris.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url
import eu.timepit.refined.types.net.PortNumber
import eu.timepit.refined.auto._
import io.odin.Level
import io.odin.formatter.Formatter
import io.odin.json.{Formatter => JFormatter}
import org.http4s.Uri

import app.ServerUrl

case class Config(server: ServerConfig, logger: LoggerConfig, counteeUri: Uri)
case class ServerConfig(port: PortNumber, apiDocs: ApiDocsConfig)
case class LoggerConfig(level: Level, formatter: Formatter)
case class ApiDocsConfig(serverUrl: ServerUrl, description: Option[String])

package object app {

  type ServerUrl = String Refined Url

  implicit val logLevelDecoder: ConfigDecoder[String, Level] =
    ConfigDecoder[String, String].mapOption("Level")(_.toLowerCase match {
      case "trace" => Level.Trace.some
      case "debug" => Level.Debug.some
      case "info"  => Level.Info.some
      case "warn"  => Level.Warn.some
      case "error" => Level.Error.some
      case _       => None
    })

  implicit val logFormatterDecoder: ConfigDecoder[String, Formatter] =
    ConfigDecoder[String, String].mapOption("Formatter")(_.toLowerCase match {
      case "default"  => Formatter.default.some
      case "colorful" => Formatter.colorful.some
      case "json"     => JFormatter.json.some
      case _          => None
    })

  private val loggerConfig: ConfigValue[LoggerConfig] = (
    env("LOG_LEVEL").as[Level].default(Level.Info),
    env("LOG_FORMATTER").as[Formatter].default(Formatter.default)
  ).parMapN(LoggerConfig)

  private val apiDocsConfig: ConfigValue[ApiDocsConfig] = (
    env("APIDOCS_SERVER_URL").as[ServerUrl].default("http://localhost:8080"),
    env("APIDOCS_DESCRIPTION").option
  ).parMapN(ApiDocsConfig)

  private val serverConfig: ConfigValue[ServerConfig] = (
    env("PORT").as[PortNumber].default(8080),
    apiDocsConfig
  ).parMapN(ServerConfig)

  implicit lazy val uriDecoder: ConfigDecoder[String, Uri] =
    ConfigDecoder
      .identity[String]
      .mapOption("Uri")(s => Uri.fromString(s).toOption)

  val config: ConfigValue[Config] = (
    serverConfig,
    loggerConfig,
    env("COUNTEE_URI").as[Uri]
  ).parMapN(Config)
}
