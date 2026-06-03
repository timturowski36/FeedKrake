CREATE TABLE articles (
    url          TEXT PRIMARY KEY,
    source       TEXT NOT NULL,
    title        TEXT NOT NULL,
    published_at TIMESTAMP,
    fetched_at   TIMESTAMP NOT NULL
);

CREATE INDEX idx_articles_source_published ON articles (source, published_at DESC NULLS LAST);
