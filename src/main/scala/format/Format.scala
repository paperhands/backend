package app.paperhands.format

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Date

object Format {
  def HHmm(t: Instant) = {
    val df = new SimpleDateFormat("HH:mm")
    df.format(Date.from(t))
  }
}

object Parse {
  def fromFullDateTime(in: String, zone: String) =
    // 2021-03-01 20:00:00
    LocalDateTime
      .parse(
        in,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
      )
      .atZone(ZoneId.of(zone))
      .toInstant
}
