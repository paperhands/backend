package app.paperhands.format

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object Format {
  def HHmm(t: Instant) = {
    val df = new SimpleDateFormat("HH:mm")
    df.format(Date.from(t))
  }

  def rfc3339(t: Instant) = {
    DateTimeFormatter.ISO_INSTANT.format(t)
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
      .atZone(ZoneId.of("UTC"))
      .toInstant
}
