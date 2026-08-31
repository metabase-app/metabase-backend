package me.lewisblackburn.metabase.credit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CreditJob {
    ACTOR("Actor");

    private final String databaseValue;
}
