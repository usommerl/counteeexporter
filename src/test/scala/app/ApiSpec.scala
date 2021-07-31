package app

import cats.data.{Kleisli, NonEmptyList}
import cats.effect.IO
import eu.timepit.refined.auto._
import io.circe.Json
import munit.CatsEffectSuite
import org.http4s.{Charset, Request, Response, Status}
import org.http4s.MediaType._
import org.http4s.dsl.io._
import org.http4s.headers.`Content-Type`
import org.http4s.implicits._

class ApiSpec extends ApiSuite {
  test("GET /metrics should return obtained records in prometheus exposition format") {
    val client       =
      counteeClient(IO.pure(NonEmptyList.of(CounteeRecord("Annaberg IZ", 0), CounteeRecord("Belgern IZ", 0))))
    val response     = api(client).run(Request[IO](method = GET, uri = uri"/metrics"))
    val expectedBody =
      s"""# HELP countee_first_counter_item_value Value of the first counter item.
         |# TYPE countee_first_counter_item_value gauge
         |countee_first_counter_item_value{name="Annaberg IZ"}\t0
         |countee_first_counter_item_value{name="Belgern IZ"}\t0\n""".stripMargin

    check(response, Ok, expectedBody)
  }
}

trait ApiSuite extends CatsEffectSuite {
  def api(client: CounteeClient[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    Api[IO](ApiDocsConfig("http://localhost:8080", None), client)

  def counteeClient(response: IO[NonEmptyList[CounteeRecord]]): CounteeClient[IO] =
    new CounteeClient[IO] {
      def fetch: IO[NonEmptyList[CounteeRecord]] = response
    }

  def check(responseIO: IO[Response[IO]], expectedStatus: Status, expectedBody: Json): IO[Unit] =
    check(responseIO, expectedStatus, Some(expectedBody.noSpaces), Some(`Content-Type`(application.json)))

  def check(responseIO: IO[Response[IO]], expectedStatus: Status, expectedBody: String): IO[Unit] =
    check(responseIO, expectedStatus, Some(expectedBody), Some(`Content-Type`(text.plain, Charset.`UTF-8`)))

  def check(
    io: IO[Response[IO]],
    expectedStatus: Status,
    expectedBody: Option[String] = None,
    expectedContentType: Option[`Content-Type`] = None,
    evaluateBody: Boolean = true
  ): IO[Unit] = io.flatMap { response =>
    assertEquals(response.status, expectedStatus)
    if (evaluateBody) {
      assertEquals(response.headers.get[`Content-Type`], expectedContentType)
      response.as[String].assertEquals(expectedBody.getOrElse(""))
    } else {
      IO.unit
    }
  }
}
