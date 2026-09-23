package me.lewisblackburn.metabase.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EntityType {
    MOVIE("MOVIE"),
    TV_SHOW("TV_SHOW"),
    TV_SEASON("TV_SEASON"),
    TV_EPISODE("TV_EPISODE"),
    BOOK("BOOK"),
    BOOK_EDITION("BOOK_EDITION"),
    SONG("SONG"),
    RECORDING("RECORDING"),
    ALBUM("ALBUM"),
    VIDEO_GAME("VIDEO_GAME"),
    PERSON("PERSON"),
    ORGANISATION("ORGANISATION"),
    COLLECTION("COLLECTION");

    private final String databaseValue;
}
