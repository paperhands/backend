CREATE TABLE sentiments (
   created_time TIMESTAMPTZ       NOT NULL,
   symbol       TEXT              NOT NULL,
   score        INTEGER           NOT NULL,
   confidence   INTEGER           NOT NULL,
   origin_id    TEXT              NOT NULL
 );

SELECT create_hypertable('sentiments', 'created_time');
