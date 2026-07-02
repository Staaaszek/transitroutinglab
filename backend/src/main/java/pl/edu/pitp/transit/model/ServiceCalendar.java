package pl.edu.pitp.transit.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public record ServiceCalendar(
        LocalDate startDate,
        LocalDate endDate,
        Set<DayOfWeek> activeDays,
        Map<LocalDate, Integer> exceptions
) {
    public boolean activeOn(LocalDate date) {
        Integer exception = exceptions.get(date);
        if (exception != null) {
            return exception == 1;
        }
        if (startDate != null && date.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && date.isAfter(endDate)) {
            return false;
        }
        return activeDays.isEmpty() || activeDays.contains(date.getDayOfWeek());
    }
}
