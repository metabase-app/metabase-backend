package me.lewisblackburn.metabase.movie.model;

import lombok.Builder;

@Builder
public record CrewMember(Long personId, String name, String department, String job) {}
