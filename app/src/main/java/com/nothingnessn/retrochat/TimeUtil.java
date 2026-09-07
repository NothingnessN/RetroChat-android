package com.nothingnessn.retrochat;

import java.util.Calendar;
import java.util.Date;

public final class TimeUtil {

    private TimeUtil() {}

    public static String formatClock(long ts) {
        Calendar cal = calOf(ts);
        return pad2(cal.get(Calendar.HOUR_OF_DAY)) + ":" + pad2(cal.get(Calendar.MINUTE));
    }

    public static String formatList(long ts) {
        if (ts <= 0) return "";
        Calendar msg = calOf(ts);
        Calendar now = Calendar.getInstance();
        if (sameDay(msg, now)) return formatClock(ts);
        Calendar yest = Calendar.getInstance();
        yest.add(Calendar.DATE, -1);
        if (sameDay(msg, yest)) return "Dun";
        int d = msg.get(Calendar.DAY_OF_MONTH);
        int mo = msg.get(Calendar.MONTH) + 1;
        int y = msg.get(Calendar.YEAR);
        if (y == now.get(Calendar.YEAR)) return pad2(d) + "." + pad2(mo);
        return pad2(d) + "." + pad2(mo) + "." + y;
    }

    public static String formatDateHeader(long ts) {
        if (ts <= 0) return "";
        Calendar msg = calOf(ts);
        Calendar now = Calendar.getInstance();
        if (sameDay(msg, now)) return "Bugun";
        Calendar yest = Calendar.getInstance();
        yest.add(Calendar.DATE, -1);
        if (sameDay(msg, yest)) return "Dun";
        return pad2(msg.get(Calendar.DAY_OF_MONTH)) + "."
                + pad2(msg.get(Calendar.MONTH) + 1) + "."
                + msg.get(Calendar.YEAR);
    }

    public static boolean sameDay(long a, long b) {
        if (a <= 0 || b <= 0) return false;
        return sameDay(calOf(a), calOf(b));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
                && a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH);
    }

    private static Calendar calOf(long ts) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(ts > 0 ? ts : System.currentTimeMillis()));
        return cal;
    }

    private static String pad2(int n) {
        return (n < 10 ? "0" : "") + n;
    }
}
