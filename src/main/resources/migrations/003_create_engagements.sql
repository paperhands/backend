CREATE TABLE IF NOT EXISTS engagements (
   created_time TIMESTAMPTZ       NOT NULL,
   symbol       TEXT              NOT NULL,
   origin_id    TEXT              NOT NULL
 );

SELECT create_hypertable('engagements', 'created_time', if_not_exists => TRUE);
