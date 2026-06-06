CREATE TABLE IF NOT EXISTS articles (
    url          VARCHAR PRIMARY KEY,
    source       VARCHAR   NOT NULL,
    title        VARCHAR   NOT NULL,
    published_at TIMESTAMP,
    fetched_at   TIMESTAMP NOT NULL
);
