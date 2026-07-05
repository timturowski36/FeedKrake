CREATE TABLE weather_day (
    location            TEXT             NOT NULL,
    day                 DATE             NOT NULL,
    weather_code        INT              NOT NULL,
    temp_max            DOUBLE PRECISION NOT NULL,
    temp_min            DOUBLE PRECISION NOT NULL,
    precip_prob_max     INT              NOT NULL,
    precip_sum_mm       DOUBLE PRECISION NOT NULL,
    wind_max_kmh        DOUBLE PRECISION NOT NULL,
    sunrise             TIME             NOT NULL,
    sunset              TIME             NOT NULL,
    fetched_at          TIMESTAMPTZ      NOT NULL,
    PRIMARY KEY (location, day)
);

CREATE TABLE weather_hour (
    location            TEXT             NOT NULL,
    ts                  TIMESTAMPTZ      NOT NULL,
    temp                DOUBLE PRECISION NOT NULL,
    precip_probability  INT              NOT NULL,
    precip_mm           DOUBLE PRECISION NOT NULL,
    weather_code        INT              NOT NULL,
    wind_kmh            DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (location, ts)
);
