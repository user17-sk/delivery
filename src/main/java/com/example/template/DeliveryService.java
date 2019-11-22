package com.example.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DeliveryService {

    @Autowired
    DeliveryRepository deliveryRepository;

    @Autowired
    private KafkaTemplate kafkaTemplate;

    @KafkaListener(topics = "${eventTopic}")
    public void onListener(@Payload String message, ConsumerRecord<?, ?> consumerRecord) {
        System.out.println("##### listener : " + message);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        OrderPlaced orderPlaced = null;
        try {
            orderPlaced = objectMapper.readValue(message, OrderPlaced.class);

            System.out.println(" #### type = " + orderPlaced.getEventType());

            /**
             * 주문이 들어옴 -> 배송 시작 이벤트 발송
             */
            if( orderPlaced.getEventType() != null && orderPlaced.getEventType().equals(OrderPlaced.class.getSimpleName())){

                Delivery delivery = new Delivery();
                delivery.setOrderId(orderPlaced.getOrderId());
                delivery.setQuantity(orderPlaced.getQuantity());
                delivery.setProductName(orderPlaced.getProductName());
                delivery.setDeliveryAddress(orderPlaced.getCustomerAddr());
                delivery.setCustomerId(orderPlaced.getCustomerId());
                delivery.setCustomerName(orderPlaced.getCustomerName());
                delivery.setDeliveryState(DeliveryStarted.class.getSimpleName());
                deliveryRepository.save(delivery);

            /**
             * 배송이 시작됨 -> 배송 완료 이벤트 발송
             */
            }else if( orderPlaced.getEventType() != null && orderPlaced.getEventType().equals(DeliveryStarted.class.getSimpleName())){

                DeliveryStarted deliveryStarted = objectMapper.readValue(message, DeliveryStarted.class);

                Delivery delivery = deliveryRepository.findById(deliveryStarted.getDeliveryId()).get();
                // TODO
                // 메모리 DB 이고, 이벤트를 처음부터 무조건 받기 때문에, 생기는 문제로, 이미 취소된상태면 완료 이벤트를 발행하지 않는다
                if( !DeliveryCancelled.class.getSimpleName().equals(delivery.getDeliveryState())) {
                    delivery.setDeliveryState(DeliveryCompleted.class.getSimpleName());
                    deliveryRepository.save(delivery);

                    String json = null;

                    try {
                        DeliveryCompleted deliveryCompleted = new DeliveryCompleted();
                        deliveryCompleted.setOrderId(deliveryStarted.getOrderId());
                        deliveryCompleted.setDeliveryId(deliveryStarted.getDeliveryId());
                        deliveryCompleted.setQuantity(deliveryStarted.getQuantity());
                        deliveryCompleted.setProductName(deliveryStarted.getProductName());
                        deliveryCompleted.setCustomerId(deliveryStarted.getCustomerId());
                        deliveryCompleted.setCustomerName(deliveryStarted.getCustomerName());
                        deliveryCompleted.setDeliveryAddress(deliveryStarted.getDeliveryAddress());
                        deliveryCompleted.setDeliveryState(DeliveryCompleted.class.getSimpleName());

                        json = objectMapper.writeValueAsString(deliveryCompleted);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("JSON format exception", e);
                    }

                    Environment env = Application.applicationContext.getEnvironment();
                    String topicName = env.getProperty("eventTopic");
                    ProducerRecord producerRecord = new ProducerRecord<>(topicName, json);
                    kafkaTemplate.send(producerRecord);
                }

            /**
             * 주문이 취소됨 -> 배송 취소 이벤트 발송
             */
            }else if( orderPlaced.getEventType().equals(OrderCancelled.class.getSimpleName())){
                List<Delivery> deliverys = deliveryRepository.findByOrderIdOrderByDeliveryIdDesc(orderPlaced.getOrderId());

                if( deliverys != null && deliverys.size() > 0 ){
                    Delivery delivery = deliverys.get(0);
                    delivery.setDeliveryState(DeliveryCancelled.class.getSimpleName());
                    deliveryRepository.save(delivery);

                    String json = null;

                    try {
                        DeliveryCancelled deliveryCancelled = new DeliveryCancelled();
                        deliveryCancelled.setOrderId(delivery.getOrderId());
                        deliveryCancelled.setDeliveryId(delivery.getDeliveryId());
                        deliveryCancelled.setQuantity(delivery.getQuantity());
                        deliveryCancelled.setProductName(delivery.getProductName());
                        deliveryCancelled.setCustomerId(delivery.getCustomerId());
                        deliveryCancelled.setCustomerName(delivery.getCustomerName());
                        deliveryCancelled.setDeliveryState(DeliveryCancelled.class.getSimpleName());

                        json = objectMapper.writeValueAsString(deliveryCancelled);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("JSON format exception", e);
                    }

                    Environment env = Application.applicationContext.getEnvironment();
                    String topicName = env.getProperty("eventTopic");
                    ProducerRecord producerRecord = new ProducerRecord<>(topicName, json);
                    kafkaTemplate.send(producerRecord);
                }

            }

        }catch (Exception e){

        }
    }
}
