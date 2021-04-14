CREATE TABLE labels (
   created_time TIMESTAMPTZ  NOT NULL,
   content_id   TEXT         NOT NULL UNIQUE,
   label        INTEGER      NOT NULL
 );
