package com.hms.api.encounter.request;
import java.time.Instant;
public record DischargeRequest(Instant dischargeAt, String dischargeNotes) {}
