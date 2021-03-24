CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE company (
       id UUID NOT NULL PRIMARY KEY,
       created_time TIMESTAMP without time zone NOT NULL DEFAULT (now() at time zone 'utc'),
       modified_time TIMESTAMP without time zone NOT NULL DEFAULT (now() at time zone 'utc'),
       public_id TEXT,
       status TEXT,
       stock_symbol TEXT,
       stock_exchange TEXT DEFAULT 'NYSE',
       profile jsonb,
       settings jsonb,
       metadata jsonb
 );
