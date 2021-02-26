-- name: create_engagement
INSERT INTO engagements(created_time, symbol, origin_id) VALUES(now(), {:symbol}, {:origin_id})

-- name: get_engagement_timeseries
SELECT
  symbol, COUNT(*) AS value, time_bucket({:bucket}, created_time) as time_interval
FROM engagements
WHERE created_time > {:start}
  AND created_time < {:end}
  AND symbol = {:symbol}
GROUP BY symbol, time_interval
ORDER BY time_interval ASC
