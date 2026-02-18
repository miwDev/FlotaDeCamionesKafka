package com.jve.producer.service;

import com.jve.model.VehicleLocation;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class GpsProducer {
    private final KafkaTemplate<String, VehicleLocation> kafkaTemplate;

    public GpsProducer(KafkaTemplate<String, VehicleLocation> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedRate = 2000)
    public void sendLocation() {
        VehicleLocation location = new VehicleLocation();
        location.setVehicleId("car-1");
        location.setLatitude(37.7728858 + (new Random().nextDouble()) / 5000);
        location.setLongitude(-3.7883289 + (new Random().nextDouble() / 5000));
        location.setTimeStamp(System.currentTimeMillis());

        kafkaTemplate.send("dakar-locations-v2", location);
    }
}
