package com.example.template;

import com.example.template.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.core.env.Environment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeliveryService {

    @Autowired
    DeliveryRepository deliveryRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void onListener(@Payload String message) {
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
                    DeliveryCompleted deliveryCompleted = new DeliveryCompleted(delivery);
                    deliveryCompleted.publish();
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

                    DeliveryCancelled deliveryCancelled = new DeliveryCancelled(delivery);
                    deliveryCancelled.publish();
                }

            }

        }catch (Exception e){

        }
    }
}
