package dan200.computercraft.shared.util;

import java.util.Calendar;

public class HolidayUtil {

    public static Holiday getCurrentHoliday() {
        return getHoliday(Calendar.getInstance());
    }

    private static Holiday getHoliday(Calendar calendar) {
        int month = calendar.get(2);
        int day = calendar.get(5);
        if (month == 1 && day == 14) {
            return Holiday.Valentines;
        } else if (month == 3 && day == 1) {
            return Holiday.AprilFoolsDay;
        } else if (month == 9 && day == 31) {
            return Holiday.Halloween;
        } else {
            return month == 11 && day >= 24 && day <= 30 ? Holiday.Christmas : Holiday.None;
        }
    }
}
