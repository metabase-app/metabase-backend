package me.lewisblackburn.metabase.movie;

public interface MovieClient<ID, T> {
    T getMovie(ID externalId);
}
