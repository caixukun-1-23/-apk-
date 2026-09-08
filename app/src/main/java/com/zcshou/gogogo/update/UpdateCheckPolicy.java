package com.acooldog.toolbox.update;

public final class UpdateCheckPolicy {
    private static final long CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L;

    private UpdateCheckPolicy() {}

    public static boolean shouldCheck(long now, long lastSuccessfulCheck) {
        return lastSuccessfulCheck <= 0L || now < lastSuccessfulCheck
                || now - lastSuccessfulCheck >= CHECK_INTERVAL_MILLIS;
    }
}
