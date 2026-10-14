package com.fixmate.util;

public final class LocationDistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private LocationDistanceCalculator() {
    }

    public static double calculateKm(double latitudeA, double longitudeA, double latitudeB, double longitudeB) {
        double latitudeDifference = Math.toRadians(latitudeB - latitudeA);
        double longitudeDifference = Math.toRadians(longitudeB - longitudeA);
        double haversine = Math.sin(latitudeDifference / 2) * Math.sin(latitudeDifference / 2)
                + Math.cos(Math.toRadians(latitudeA)) * Math.cos(Math.toRadians(latitudeB))
                * Math.sin(longitudeDifference / 2) * Math.sin(longitudeDifference / 2);
        double boundedHaversine = Math.min(1.0, Math.max(0.0, haversine));
        return EARTH_RADIUS_KM * 2 * Math.atan2(
                Math.sqrt(boundedHaversine),
                Math.sqrt(1.0 - boundedHaversine)
        );
    }
}