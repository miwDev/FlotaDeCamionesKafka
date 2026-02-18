package com.jve.producer.service;

import java.util.Random;

public class RandomLocationGenerator {

    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;
    private static final double MIN_LON = -180.0;
    private static final double MAX_LON = 180.0;
    private Random random = new Random();

    public String generateRandomLocation() {
        double latitude = MIN_LAT + (MAX_LAT - MIN_LAT) * random.nextDouble();
        double longitude = MIN_LON + (MAX_LON - MIN_LON) * random.nextDouble();
        return "lat:" + latitude + ",lon:" + longitude;
    }
}