CREATE TABLE IF NOT EXISTS content (
       id           TEXT PRIMARY KEY,
       created_time TIMESTAMP NOT NULL DEFAULT now(),
       origin_time  TIMESTAMP,
       type         TEXT,
       origin       TEXT,
       parent_id    TEXT,
       permalink    TEXT NOT NULL,
       parsed       JSONB,
       author       TEXT,
       body         TEXT
 );
