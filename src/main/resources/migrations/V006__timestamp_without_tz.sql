ALTER TABLE engagements ALTER created_time TYPE timestamp USING created_time AT TIME ZONE 'UTC';
ALTER TABLE sentiments ALTER created_time TYPE timestamp USING created_time AT TIME ZONE 'UTC';
ALTER TABLE ocr_cache ALTER created_time TYPE timestamp USING created_time AT TIME ZONE 'UTC';
