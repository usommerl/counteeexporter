package app

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import io.circe._
import io.circe.CursorOp.DownField
import org.http4s.Uri
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client

trait CounteeClient[F[_]] {
  def fetch: F[NonEmptyList[CounteeRecord]]
}

object CounteeClient {

  def apply[F[_]: Sync](http: Client[F], counteeUri: Uri) = new CounteeClient[F] {

    override def fetch: F[NonEmptyList[CounteeRecord]] =
      http.expect[NonEmptyList[CounteeRecord]](counteeUri)

    private implicit val recordDecoder: Decoder[CounteeRecord] = (c: HCursor) =>
      for {
        name                <- c.get[String]("name")
        firstCounterItemVal <- c.downField("counteritems").downArray.get[Int]("val")
      } yield (CounteeRecord(name, firstCounterItemVal))

    private implicit val counteeResponseDecoder: Decoder[NonEmptyList[CounteeRecord]] = (c: HCursor) => {
      c.downField("response")
        .downField("data")
        .as[Map[String, CounteeRecord]]
        .flatMap(_.values.toList.toNel.toRight(DecodingFailure("No data records", List(DownField("response"), DownField("data")))))
    }
  }
}

case class CounteeRecord(name: String, firstCounterItemVal: Int)
