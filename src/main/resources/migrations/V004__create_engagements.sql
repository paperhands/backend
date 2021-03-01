CREATE TABLE engagements (
   created_time TIMESTAMPTZ       NOT NULL,
   symbol       TEXT              NOT NULL,
   origin_id    TEXT              NOT NULL
 );

SELECT create_hypertable('engagements', 'created_time');
