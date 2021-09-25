package app

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.kernel.Async
import cats.implicits._
import dev.usommerl.BuildInfo
import eu.timepit.refined.auto._
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.implicits._
import org.http4s.server.middleware.CORS
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.apispec.Tag
import sttp.tapir.docs.openapi._
import sttp.tapir.openapi.{OpenAPI, Server}
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.SwaggerUI

object Api {
  def apply[F[_]: Async](config: ApiDocsConfig, client: CounteeClient[F]): Kleisli[F, Request[F], Response[F]] = {

    val dsl = Http4sDsl[F]
    import dsl._

    val apis: List[TapirApi[F]] = List(MetricsApi(client))

    val docs: OpenAPI = OpenAPIDocsInterpreter()
      .toOpenAPI(apis.flatMap(_.endpoints), openapi.Info(BuildInfo.name, BuildInfo.version, config.description))
      .servers(List(Server(config.serverUrl)))
      .tags(apis.map(_.tag))

    val swaggerUi          = Http4sServerInterpreter().toRoutes(SwaggerUI[F](docs.toYaml))
    val redirectRootToDocs = HttpRoutes.of[F] { case path @ GET -> Root => PermanentRedirect(Location(path.uri / "docs")) }

    val routes: List[HttpRoutes[F]] = apis.map(_.routes) ++ List(swaggerUi, redirectRootToDocs)

    CORS.policy(routes.reduce(_ <+> _)).orNotFound
  }
}

object MetricsApi                    {
  def apply[F[_]: Async](client: CounteeClient[F]) = new TapirApi[F] {
    override val tag                  = Tag("Metrics", None)
    override lazy val serverEndpoints = List(metrics)

    private val metrics: ServerEndpoint[Unit, StatusCode, String, Any, F] =
      endpoint.get
        .summary("Countee counter values as Prometheus metrics")
        .tag(tag.name)
        .in("metrics")
        .out(stringBody)
        .errorOut(statusCode)
        .serverLogic(_ => client.fetch.map(toExpositionFormat(_).asRight))

    /** See: https://github.com/prometheus/docs/blob/master/content/docs/instrumenting/exposition_formats.md
      */
    private def toExpositionFormat(l: NonEmptyList[CounteeRecord]): String = {
      val metricName = "countee_first_counter_item_value"
      val doc        = s"# HELP $metricName Value of the first counter item.\n# TYPE $metricName gauge\n"
      l.foldLeft(doc) { case (acc, r) =>
        s"""$acc$metricName{name="${r.name}"}\t${r.firstCounterItemVal}\n"""
      }
    }
  }
}

abstract class TapirApi[F[_]: Async] {
  def tag: Tag
  def serverEndpoints: List[ServerEndpoint[_, _, _, Any, F]]
  def endpoints: List[Endpoint[_, _, _, _]] = serverEndpoints.map(_.endpoint)
  def routes: HttpRoutes[F]                 = Http4sServerInterpreter().toRoutes(serverEndpoints)
}
