package com.stock.platform.backend_api.api.dto;

public record BreadthSnapshotDto(
        String indexSymbol,
        String asOfDate,
        int totalMembers,
        int membersWithData,
        int up,
        int down,
        int flat,
        int aboveMa20,
        int aboveMa50,
        int aboveMa200,
        int newHigh52w,
        int newLow52w,
        int volumeSurge
) {
}

