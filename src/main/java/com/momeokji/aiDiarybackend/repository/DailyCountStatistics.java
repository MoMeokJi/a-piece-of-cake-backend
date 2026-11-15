package com.momeokji.aiDiarybackend.repository;

import java.time.LocalDate;

public interface DailyCountStatistics {

	LocalDate getDate();

	Long getTotalCount();
}