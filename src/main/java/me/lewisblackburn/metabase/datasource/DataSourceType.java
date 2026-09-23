package me.lewisblackburn.metabase.datasource;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DataSourceType {
    API("API"),
    FILE("FILE"),
    MANUAL("MANUAL"),
    SCRAPER("SCRAPER"),
    OTHER("OTHER");

    private final String databaseValue;
}
