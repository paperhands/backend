ALTER TABLE engagements ALTER created_time TYPE timestamp without time zone USING created_time AT TIME ZONE 'UTC';
ALTER TABLE engagements ALTER created_time SET DEFAULT (now() at time zone 'utc') ;
ALTER TABLE sentiments ALTER created_time TYPE timestamp without time zone USING created_time AT TIME ZONE 'UTC';
ALTER TABLE sentiments ALTER created_time SET DEFAULT (now() at time zone 'utc');
ALTER TABLE ocr_cache ALTER created_time TYPE timestamp without time zone USING created_time AT TIME ZONE 'UTC';
ALTER TABLE sentiments ALTER created_time SET DEFAULT (now() at time zone 'utc');
