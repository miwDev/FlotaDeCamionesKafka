package com.jve.consumer.service;

import com.jve.model.VehicleLocation;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class GpsConsumer {
    private final SimpMessagingTemplate messagingTemplate;

    public GpsConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "dakar-locations-v2", groupId = "gps-group")
    public void consume(VehicleLocation location) {
        messagingTemplate.convertAndSend("/topic/locations", location);

    }
}
