-- name: create_sentiment
INSERT INTO
sentiments(created_time, symbol, score, confidence, origin_id)
VALUES(now(), {:symbol}, {:score}, {:confidence}, {:origin_id})

-- name: get_trending
SELECT
  symbol,COUNT(score) AS popularity
FROM sentiments
WHERE created_time > {:start}
  AND created_time < {:end}
GROUP BY symbol
ORDER BY COUNT(score)
DESC LIMIT {:limit}

-- name: get_mention_timeseries
SELECT
  symbol, COUNT(score) AS value, time_bucket({:bucket}, created_time) as time_interval
FROM sentiments
WHERE created_time > {:start}
  AND created_time < {:end}
  AND symbol = {:symbol}
GROUP BY symbol, time_interval
ORDER BY time_interval ASC

-- name: get_sentiment_timeseries
SELECT
  time_bucket({:bucket}, created_time) AS time_interval,
  SUM(
    CASE
      WHEN score = 2 THEN -1
      WHEN score < 2 THEN score
    END
  ) AS value,
  symbol
FROM
  sentiments
WHERE created_time > {:start}
  AND created_time < {:end}
  AND symbol = {:symbol}
GROUP BY
  symbol,
  time_interval
ORDER BY
  time_interval ASC
