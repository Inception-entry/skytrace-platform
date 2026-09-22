package com.skytrace.backend.alarm.service;

import com.skytrace.backend.alarm.dto.AlarmResponse;

public record AlarmCreateResult(AlarmResponse alarm, boolean inserted) {
}
