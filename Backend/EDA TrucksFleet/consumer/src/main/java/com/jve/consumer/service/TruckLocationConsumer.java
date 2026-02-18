package com.jve.consumer.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class TruckLocationConsumer {

    private Map<String, String> truckLocations = new HashMap<>();

    @KafkaListener(topics = "truck-locations", groupId = "truck-group")
    public void consume(ConsumerRecord<String, String> record) {
        String truckId = record.key();
        String location = record.value();
        truckLocations.put(truckId, location);
        displayTruckLocations();
    }

    public void displayTruckLocations() {
        System.out.println("Current Truck Locations:");
        truckLocations.forEach((truckId, location) -> 
            System.out.println("Truck ID: " + truckId + " Location: " + location));
    }
}