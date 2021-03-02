package app.paperhands.popularity

import app.paperhands.model

case class PopularityResponse(
    symbol: String,
    mentions: Int,
    users: Int
)

object Popularity {
  def fromQuery(input: model.Popularity): PopularityResponse = {
    PopularityResponse(
      input.symbol,
      input.mentions,
      input.engagements
    )
  }
}
