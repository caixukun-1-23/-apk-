package com.acooldog.toolbox.route.data;

import com.acooldog.toolbox.route.domain.model.RoutePoint;
import com.acooldog.toolbox.utils.MapUtils;

import org.json.JSONException;
import org.json.JSONObject;

public final class RoutePointJsonCodec {
    public static final String KEY_BD_LONGITUDE = "bdLongitude";
    public static final String KEY_BD_LATITUDE = "bdLatitude";
    public static final String KEY_WGS_LONGITUDE = "wgsLongitude";
    public static final String KEY_WGS_LATITUDE = "wgsLatitude";
    public static final String KEY_ALTITUDE = "altitude";

    private RoutePointJsonCodec() {
    }

    public static JSONObject encode(RoutePoint point) throws JSONException {
        JSONObject pointJson = new JSONObject();
        pointJson.put(KEY_BD_LONGITUDE, point.getBdLongitude());
        pointJson.put(KEY_BD_LATITUDE, point.getBdLatitude());
        pointJson.put(KEY_WGS_LONGITUDE, point.getWgsLongitude());
        pointJson.put(KEY_WGS_LATITUDE, point.getWgsLatitude());
        pointJson.put(KEY_ALTITUDE, point.getAltitude());
        return pointJson;
    }

    public static RoutePoint decode(JSONObject pointJson) throws JSONException {
        Double bdLongitude = optDouble(pointJson, KEY_BD_LONGITUDE, "bdLng", "longitude", "lng");
        Double bdLatitude = optDouble(pointJson, KEY_BD_LATITUDE, "bdLat", "latitude", "lat");
        Double wgsLongitude = optDouble(pointJson, KEY_WGS_LONGITUDE, "gpsLongitude", "wgsLng");
        Double wgsLatitude = optDouble(pointJson, KEY_WGS_LATITUDE, "gpsLatitude", "wgsLat");
        double altitude = pointJson.optDouble(KEY_ALTITUDE, 55d);

        if (bdLongitude == null || bdLatitude == null) {
            if (wgsLongitude == null || wgsLatitude == null) {
                throw new JSONException("Point coordinates are missing");
            }
            double[] bdCoordinates = MapUtils.wgs2bd09(wgsLongitude, wgsLatitude);
            bdLongitude = bdCoordinates[0];
            bdLatitude = bdCoordinates[1];
        }

        if (wgsLongitude == null || wgsLatitude == null) {
            double[] wgsCoordinates = MapUtils.bd2wgs(bdLongitude, bdLatitude);
            wgsLongitude = wgsCoordinates[0];
            wgsLatitude = wgsCoordinates[1];
        }

        if (!validCoordinate(bdLongitude, 180d) || !validCoordinate(bdLatitude, 90d)
                || !validCoordinate(wgsLongitude, 180d) || !validCoordinate(wgsLatitude, 90d)
                || !Double.isFinite(altitude)) {
            throw new JSONException("Point coordinates must be finite and within valid bounds");
        }
        return new RoutePoint(bdLongitude, bdLatitude, wgsLongitude, wgsLatitude, altitude);
    }

    private static boolean validCoordinate(double value, double limit) {
        return Double.isFinite(value) && Math.abs(value) <= limit;
    }

    private static Double optDouble(JSONObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key)) {
                return object.optDouble(key);
            }
        }
        return null;
    }
}
