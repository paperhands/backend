CREATE TABLE ocr_cache (
   created_time TIMESTAMPTZ       NOT NULL,
   url          TEXT              NOT NULL,
   output       TEXT
 );
