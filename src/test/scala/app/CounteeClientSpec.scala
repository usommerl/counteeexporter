package app

import cats.implicits._
import cats.effect.IO
import munit.CatsEffectSuite
import org.http4s.dsl.io._
import org.http4s.Uri
import org.http4s.HttpApp
import org.http4s.client.Client
import org.http4s.Response
import cats.data.NonEmptyList

class CounteeClientSpec extends CatsEffectSuite {

  test("Decoding of a valid API response should yield a list of records") {
    val mockResponse = Ok(excerptOfManuallyRecordedResponse)
    val expected     = NonEmptyList.of(CounteeRecord("Annaberg IZ", 0), CounteeRecord("Belgern IZ", 0))
    client(mockResponse).fetch.map(obtained => assertEquals(obtained, expected))
  }

  test("Decoding of a API response without recors should yield a decoding error") {
    val mockResponse = Ok("""{"response": { "data": {}}}""")
    client(mockResponse).fetch.attempt.map(r =>
      assertEquals(r.leftMap(_.getCause.getMessage), Left("No data records: DownField(response),DownField(data)"))
    )
  }

  private def client(response: IO[Response[IO]]): CounteeClient[IO] = {
    val uri        = Uri.unsafeFromString("https://countee-impfee.b-cdn.net/api/1.1/de/counters/getAll/_iz_sachsen")
    val mockServer = HttpApp[IO] {
      case GET -> Root / "api" / "1.1" / "de" / "counters" / "getAll" / "_iz_sachsen" => response
      case _                                                                          => ???
    }
    CounteeClient(Client.fromHttpApp(mockServer), uri)
  }

  private val excerptOfManuallyRecordedResponse =
    raw"""
{
  "response": {
    "data": {
      "c6034d67bd4720": {
        "id": 552,
        "name": "Annaberg IZ",
        "msg": null,
        "slug": "c6034d67bd4720",
        "slug_badi_info": null,
        "max": 10,
        "max_show_avail_txt": null,
        "mode_display": "available_calc",
        "closed": 0,
        "status": 1,
        "counteritems": [
          {
            "id": "1953692",
            "val": 0,
            "val_s": "[{\"c\":0,\"d\":1620856800},{\"c\":0,\"d\":1620943200},{\"c\":0,\"d\":1621029600},{\"c\":0,\"d\":1621116000},{\"c\":0,\"d\":1621202400},{\"c\":0,\"d\":1621288800},{\"c\":0,\"d\":1621375200},{\"c\":0,\"d\":1621461600},{\"c\":0,\"d\":1621548000},{\"c\":0,\"d\":1621634400},{\"c\":0,\"d\":1621720800},{\"c\":0,\"d\":1621807200},{\"c\":0,\"d\":1621893600}]",
            "ts_created": "1620836932",
            "counter_id": "552"
          }
        ]
      },
      "c2034d6a1f23b4": {
        "id": 548,
        "name": "Belgern IZ",
        "msg": null,
        "slug": "c2034d6a1f23b4",
        "slug_badi_info": null,
        "max": 10,
        "max_show_avail_txt": null,
        "mode_display": "available_calc",
        "closed": 0,
        "status": 1,
        "counteritems": [
          {
            "id": "1953030",
            "val": 0,
            "val_s": "[{\"c\":0,\"d\":1620856800},{\"c\":0,\"d\":1620943200},{\"c\":0,\"d\":1621029600},{\"c\":0,\"d\":1621116000},{\"c\":0,\"d\":1621202400},{\"c\":0,\"d\":1621288800},{\"c\":0,\"d\":1621375200},{\"c\":0,\"d\":1621461600},{\"c\":0,\"d\":1621548000},{\"c\":0,\"d\":1621634400},{\"c\":0,\"d\":1621720800},{\"c\":0,\"d\":1621807200},{\"c\":0,\"d\":1621893600}]",
            "ts_created": "1620804532",
            "counter_id": "548"
          }
        ]
      }
    }
  }
}"""

}
