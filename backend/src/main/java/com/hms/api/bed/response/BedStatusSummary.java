package com.hms.api.bed.response;
public record BedStatusSummary(long total, long available, long allocated, long maintenance) {}
