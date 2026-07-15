package com.anything.momeogji.dto.recommendation;

/** 참여자 1명이 후보 음식점 1곳에 던진 투표. 여러 후보에 중복 투표할 수 있으므로 여러 건이 올 수 있다. */
public record VoteRequest(String voterId, String restaurantName) {
}
