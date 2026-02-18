WALKTHROUGH: SISTEMA DE GPS PARA FLOTA DE CAMIONES CON KAFKA
ÍNDICE
Arquitectura general

Requisitos previos

Paso 1: Levantar infraestructura con Docker

Paso 2: Crear y ejecutar el Producer

Paso 3: Crear y ejecutar el Consumer

Paso 4: Abrir el Frontend y ver el mapa

Paso 5: Verificar el funcionamiento

Solución de problemas comunes

ARQUITECTURA GENERAL
El sistema consta de cuatro componentes:

Producer (Spring Boot, puerto 8282): Simula un GPS de camión generando coordenadas cada 2 segundos y las envía a Kafka.

Kafka (Docker, puerto 9092): Broker de mensajes que recibe y distribuye las ubicaciones.

Consumer (Spring Boot, puerto 8181): Escucha las ubicaciones desde Kafka y las reenvía a través de WebSocket a los clientes web.

Frontend (HTML/JavaScript): Mapa Leaflet que se conecta por WebSocket al Consumer y dibuja los marcadores en tiempo real.

Flujo de datos:
Producer -> Kafka -> Consumer -> WebSocket -> Frontend

REQUISITOS PREVIOS
Docker y Docker Compose instalados.

Java 21 o superior.

Maven (o usar los wrappers mvnw incluidos).

Navegador web moderno.

PASO 1: LEVANTAR INFRAESTRUCTURA CON DOCKER
Crea un archivo llamado docker-compose.yaml con el siguiente contenido:

yaml
version: "3.8"
services:
  zookeeper:
    image: zookeeper
    ports:
      - "2181:2181"
    networks:
      - kafka-network

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    networks:
      - kafka-network

  kafka-manager:
    image: hlebalbau/kafka-manager:latest
    depends_on:
      - zookeeper
      - kafka
    ports:
      - "9000:9000"
    environment:
      ZK_HOSTS: zookeeper:2181
    networks:
      - kafka-network

networks:
  kafka-network:
    driver: bridge
Ejecuta en la terminal:

bash
docker-compose up -d
Esto levantará:

Zookeeper en localhost:2181

Kafka en localhost:9092

Kafka Manager en http://localhost:9000 (para administración opcional)

Verifica que los contenedores estén corriendo:

bash
docker ps
PASO 2: CREAR Y EJECUTAR EL PRODUCER
Estructura de carpetas del Producer
text
producer/
├── pom.xml
├── src/main/java/com/jve/
│   ├── model/VehicleLocation.java
│   ├── producer/ProducerApplication.java
│   └── producer/service/GpsProducer.java
└── src/main/resources/application.properties
Archivo pom.xml (dependencias principales)
xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
</dependencies>
Archivo VehicleLocation.java (modelo)
java
package com.jve.model;

public class VehicleLocation {
    private String vehicleId;
    private double latitude;
    private double longitude;
    private long timeStamp;

    public VehicleLocation() {}

    public VehicleLocation(String vehicleId, double latitude, double longitude, long timeStamp) {
        this.vehicleId = vehicleId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeStamp = timeStamp;
    }

    // Getters y setters (imprescindibles para la serialización JSON)
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public long getTimeStamp() { return timeStamp; }
    public void setTimeStamp(long timeStamp) { this.timeStamp = timeStamp; }
}
Archivo GpsProducer.java (servicio que genera ubicaciones)
java
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

    @Scheduled(fixedRate = 2000) // Se ejecuta cada 2 segundos
    public void sendLocation() {
        VehicleLocation location = new VehicleLocation();
        location.setVehicleId("car-1");
        // Pequeña variación aleatoria para simular movimiento
        location.setLatitude(37.7728858 + (new Random().nextDouble()) / 5000);
        location.setLongitude(-3.7883289 + (new Random().nextDouble() / 5000));
        location.setTimeStamp(System.currentTimeMillis());

        // Enviar al topic "dakar-locations-v2"
        kafkaTemplate.send("dakar-locations-v2", location);
        System.out.println("Enviado: " + location.getVehicleId() + " en " + location.getLatitude() + ", " + location.getLongitude());
    }
}
Archivo application.properties (configuración del producer)
properties
spring.application.name=producer
server.port=8282
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
Clase principal ProducerApplication.java
java
package com.jve.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // Habilita las tareas programadas (@Scheduled)
public class ProducerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }
}
Compila y ejecuta el producer:

bash
cd producer
./mvnw spring-boot:run
Verás mensajes cada 2 segundos indicando que se envían ubicaciones.

PASO 3: CREAR Y EJECUTAR EL CONSUMER
Estructura de carpetas del Consumer
text
consumer/
├── pom.xml
├── src/main/java/com/jve/
│   ├── model/VehicleLocation.java (mismo que en producer)
│   ├── consumer/ConsumerApplication.java
│   ├── consumer/WebSocketConfig.java
│   ├── consumer/service/GpsConsumer.java
└── src/main/resources/application.properties
Archivo pom.xml (añade dependencia de WebSocket)
xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
</dependencies>
Archivo VehicleLocation.java (idéntico al del producer)
Archivo WebSocketConfig.java (configuración STOMP)
java
package com.jve.consumer;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Prefijo para los mensajes enviados desde el servidor a los clientes
        config.enableSimpleBroker("/topic");
        // Prefijo para mensajes que los clientes envían al servidor (si los hubiera)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket con soporte SockJS
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
Archivo GpsConsumer.java (consume de Kafka y reenvía por WebSocket)
java
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
        // Envía la ubicación a todos los clientes suscritos a "/topic/locations"
        messagingTemplate.convertAndSend("/topic/locations", location);
        System.out.println("Reenviado: " + location.getVehicleId());
    }
}
Archivo application.properties (configuración del consumer)
properties
spring.application.name=consumer
server.port=8181
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=truck-group
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=com.jve.model
spring.kafka.consumer.auto-offset-reset=latest
Clase principal ConsumerApplication.java
java
package com.jve.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
Compila y ejecuta el consumer:

bash
cd consumer
./mvnw spring-boot:run
Verás mensajes cada vez que llegue una ubicación desde Kafka.

PASO 4: ABRIR EL FRONTEND Y VER EL MAPA
Crea un archivo index.html en cualquier carpeta (o dentro del consumer en src/main/resources/static/ para servirlo estáticamente). Copia el siguiente código:

html
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Flota de Camiones GPS</title>
    <link rel="stylesheet" href="https://unpkg.com/leaflet/dist/leaflet.css  " />
</head>
<body>
    <div id="map" style="height: 600px"></div>

    <script src="https://unpkg.com/leaflet/dist/leaflet.js  "></script>
    <script src="https://cdn.jsdelivr.net/npm/sockjs-client/dist/sockjs.min.js  "></script>
    <script src="https://cdn.jsdelivr.net/npm/stompjs/lib/stomp.min.js  "></script>

    <script>
        // 1. Inicializar mapa centrado en una zona (cambia las coordenadas si quieres)
        var map = L.map("map").setView([-23.5, -46.6], 6);
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            attribution: "© OpenStreetMap",
        }).addTo(map);

        // 2. Objeto para guardar los marcadores de cada vehículo
        var markers = {};

        // 3. Conectar al WebSocket del consumer (AJUSTA LA IP)
        // Si el consumer corre en tu misma máquina, usa localhost:8181
        var socket = new SockJS("http://192.168.121.91:8181/ws");
        var stompClient = Stomp.over(socket);

        stompClient.connect({}, function () {
            console.log("Conectado al WebSocket");
            // 4. Suscribirse al topic donde se publican ubicaciones
            stompClient.subscribe("/topic/locations", function (message) {
                var location = JSON.parse(message.body);

                // 5. Si el vehículo no tiene marcador, lo crea; si ya tiene, lo mueve
                if (!markers[location.vehicleId]) {
                    markers[location.vehicleId] = L.marker([
                        location.latitude,
                        location.longitude,
                    ])
                        .addTo(map)
                        .bindPopup(location.vehicleId);
                } else {
                    markers[location.vehicleId].setLatLng([
                        location.latitude,
                        location.longitude,
                    ]);
                }
            });
        });
    </script>
</body>
</html>
Importante: Reemplaza 192.168.121.91 por la IP o nombre del equipo donde se ejecuta el consumer. Si todo está en localhost, usa http://localhost:8181/ws.

Abre el archivo index.html con un navegador (doble clic o sírvelo con un servidor como npx http-server).

PASO 5: VERIFICAR EL FUNCIONAMIENTO
Docker: Debe tener los contenedores de Zookeeper y Kafka corriendo.

bash
docker ps
Producer: En la consola donde corre, deberías ver líneas como:

text
Enviado: car-1 en 37.7729, -3.7883
Consumer: En su consola, deberías ver:

text
Reenviado: car-1
Frontend: En el mapa debería aparecer un marcador que se mueve ligeramente cada 2 segundos. Abre la consola del navegador (F12) y busca mensajes como "Conectado al WebSocket" o errores de conexión.

Prueba manual (opcional): Puedes ver el topic de Kafka con Kafka Manager en http://localhost:9000 para comprobar que los mensajes están llegando.

SOLUCIÓN DE PROBLEMAS COMUNES
Problema	Posible causa	Solución
El frontend no conecta	IP/puerto incorrecto en SockJS	Verifica que el consumer corre en el puerto 8181 y la IP es accesible. Prueba con localhost si todo está en la misma máquina.
No llegan mensajes al consumer	Kafka no está corriendo o topic no existe	Revisa docker ps. Kafka crea el topic automáticamente al primer mensaje.
Error de serialización en consumer	Los paquetes com.jve.model no están en trusted.packages	Asegura la propiedad en application.properties.
El marcador no se mueve	El frontend no recibe mensajes	Abre consola del navegador y mira si hay errores de conexión WebSocket.
Producer lanza excepción de conexión	Kafka no está en localhost:9092	Verifica que el contenedor Kafka está corriendo y expone el puerto correcto.
¡Ya tienes tu sistema funcionando! Este walkthrough te guía desde cero hasta tener un mapa en tiempo real con posiciones simuladas de un camión. Puedes extenderlo fácilmente añadiendo más vehículos o persistencia.

puedes darme todo esto organizado en formato markdown para mi readme?
