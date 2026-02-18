# 🚚 Sistema de GPS para Flota de Camiones con Kafka

Sistema distribuido en tiempo real para simular el seguimiento GPS de una flota de camiones usando:

- Spring Boot
- Apache Kafka
- WebSocket (STOMP + SockJS)
- Leaflet (Mapa en tiempo real)
- Docker

---

# 📑 Índice

1. Arquitectura General
2. Requisitos Previos
3. Paso 1: Levantar Infraestructura con Docker
4. Paso 2: Crear y Ejecutar el Producer
5. Paso 3: Crear y Ejecutar el Consumer
6. Paso 4: Frontend y Mapa en Tiempo Real
7. Paso 5: Verificación del Funcionamiento
8. Solución de Problemas

---

# 🏗 Arquitectura General

El sistema consta de 4 componentes principales:

| Componente | Puerto | Descripción |
|------------|--------|-------------|
| Producer | 8282 | Simula GPS y envía ubicaciones cada 2 segundos |
| Kafka | 9092 | Broker de mensajería |
| Consumer | 8181 | Consume de Kafka y reenvía por WebSocket |
| Frontend | - | Mapa Leaflet que muestra posiciones en tiempo real |

## 🔄 Flujo de datos

Producer → Kafka → Consumer → WebSocket → Frontend

---

# ⚙ Requisitos Previos

- Docker
- Docker Compose
- Java 21+
- Maven (o mvnw)
- Navegador moderno

---

# 🐳 Paso 1: Levantar Infraestructura con Docker

## Crear docker-compose.yaml

```yaml
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
```

## Levantar contenedores

```bash
docker-compose up -d
```

Verificar:

```bash
docker ps
```

---

# 🚛 Paso 2: Crear y Ejecutar el Producer

## Estructura

```
producer/
├── pom.xml
├── src/main/java/com/jve/
│   ├── model/VehicleLocation.java
│   ├── producer/ProducerApplication.java
│   └── producer/service/GpsProducer.java
└── src/main/resources/application.properties
```

## pom.xml

```xml
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
```

## VehicleLocation.java

```java
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

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public long getTimeStamp() { return timeStamp; }
    public void setTimeStamp(long timeStamp) { this.timeStamp = timeStamp; }
}
```

## GpsProducer.java

```java
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
        System.out.println("Enviado: " + location.getVehicleId());
    }
}
```

## application.properties

```properties
spring.application.name=producer
server.port=8282
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
```

## Ejecutar Producer

```bash
cd producer
./mvnw spring-boot:run
```

---

# 📡 Paso 3: Crear y Ejecutar el Consumer

## Estructura

```
consumer/
├── pom.xml
├── src/main/java/com/jve/
│   ├── model/VehicleLocation.java
│   ├── consumer/ConsumerApplication.java
│   ├── consumer/WebSocketConfig.java
│   ├── consumer/service/GpsConsumer.java
└── src/main/resources/application.properties
```

## Dependencia adicional

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

## WebSocketConfig.java

```java
@EnableWebSocketMessageBroker
@Configuration
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```

## GpsConsumer.java

```java
@Service
public class GpsConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    public GpsConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "dakar-locations-v2", groupId = "gps-group")
    public void consume(VehicleLocation location) {
        messagingTemplate.convertAndSend("/topic/locations", location);
        System.out.println("Reenviado: " + location.getVehicleId());
    }
}
```

## application.properties

```properties
spring.application.name=consumer
server.port=8181
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=truck-group
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=com.jve.model
spring.kafka.consumer.auto-offset-reset=latest
```

---

# 🗺 Paso 4: Frontend

Crear index.html:

```html
<!doctype html>
<html>
<head>
    <meta charset="UTF-8" />
    <title>Flota de Camiones GPS</title>
    <link rel="stylesheet" href="https://unpkg.com/leaflet/dist/leaflet.css" />
</head>
<body>
<div id="map" style="height: 600px"></div>

<script src="https://unpkg.com/leaflet/dist/leaflet.js"></script>
<script src="https://cdn.jsdelivr.net/npm/sockjs-client/dist/sockjs.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/stompjs/lib/stomp.min.js"></script>

<script>
    var map = L.map("map").setView([-23.5, -46.6], 6);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png").addTo(map);

    var markers = {};
    var socket = new SockJS("http://localhost:8181/ws");
    var stompClient = Stomp.over(socket);

    stompClient.connect({}, function () {
        stompClient.subscribe("/topic/locations", function (message) {
            var location = JSON.parse(message.body);

            if (!markers[location.vehicleId]) {
                markers[location.vehicleId] = L.marker([
                    location.latitude,
                    location.longitude
                ]).addTo(map);
            } else {
                markers[location.vehicleId].setLatLng([
                    location.latitude,
                    location.longitude
                ]);
            }
        });
    });
</script>
</body>
</html>
```

---

# ✅ Verificación

Docker:

```bash
docker ps
```

Producer:

Enviado: car-1

Consumer:

Reenviado: car-1

Frontend:

- Marcador visible
- Movimiento cada 2 segundos

---

# 🛠 Solución de Problemas

| Problema | Posible Causa | Solución |
|-----------|---------------|----------|
| Frontend no conecta | IP incorrecta | Verificar localhost:8181 |
| Consumer no recibe | Kafka no corre | docker ps |
| Error de serialización | trusted.packages mal configurado | Revisar application.properties |
| Marker no se mueve | WebSocket desconectado | Revisar consola navegador |
| Producer falla conexión | Kafka no expone 9092 | Verificar docker-compose |

---

# 🚀 Posibles Extensiones

- Múltiples vehículos
- Persistencia en base de datos
- Autenticación JWT
- Dockerizar Producer y Consumer
- Kubernetes
