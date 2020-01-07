package com.example.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class DeliveryController {

    private static final String RESPONSE_STRING_FORMAT = "delivery start from '%s': %d\n";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private int count = 0;

    private static final String HOSTNAME = parseContainerIdFromHostname(
            System.getenv().getOrDefault("HOSTNAME", "unknown"));

    static String parseContainerIdFromHostname(String hostname) {
        return hostname.replaceAll("order-v\\d+-", "");
    }

    @GetMapping("/startDelivery")
    public ResponseEntity<String> startDelivery() {
        count++;
        logger.debug(String.format("delivery start from %s: %d", HOSTNAME, count));
        return ResponseEntity.ok(String.format(DeliveryController.RESPONSE_STRING_FORMAT, HOSTNAME, count));
    }

}
