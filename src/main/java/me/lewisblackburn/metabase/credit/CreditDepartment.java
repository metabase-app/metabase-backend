package me.lewisblackburn.metabase.credit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CreditDepartment {
    ACTING("Acting");

    private final String databaseValue;
}
