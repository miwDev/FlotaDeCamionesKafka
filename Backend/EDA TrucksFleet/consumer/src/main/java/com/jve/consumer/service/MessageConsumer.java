package com.jve.consumer.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


@Service
public class MessageConsumer {

    private Map<String, String> chat = new HashMap<>();

    @KafkaListener(topics = "SpainMood", groupId = "consumer_1_id")
    public void consume(ConsumerRecord<String, String> record) {
        String quien = record.key();
        String quedices = record.value();
        chat.put(quien, quedices);
        displayChat();
    }

    public void displayChat() {
            chat.forEach((quien, que) -> 
            System.out.println( quien + que));
    }

   
}


