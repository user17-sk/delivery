package com.example.template;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
public class DeliveryController {

    private static final String RESPONSE_STRING_FORMAT = "delivery start from '%s': %s";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    RestTemplate restTemplate;

    @Value("${api.url.product:http://products:8080}")
    private String remoteURL;

    private int count = 0;

    private static final String HOSTNAME = parseContainerIdFromHostname(
            System.getenv().getOrDefault("HOSTNAME", "delivery"));

    static String parseContainerIdFromHostname(String hostname) {
        return hostname.replaceAll("delivery-\\d+-", "");
    }

    @PostMapping("/startDelivery")
    public ResponseEntity<String> fakeStartDelivery(@RequestBody String data) {
        count++;
        logger.info(String.format("delivery start from %s: %d", HOSTNAME, count));

        JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(data);
        String productId = element.getAsJsonObject().get("productId").getAsString();
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        try {
            String result = restTemplate.patchForObject(remoteURL + "/product/"+productId, data, String.class);
            return ResponseEntity.ok(String.format(RESPONSE_STRING_FORMAT, HOSTNAME, result));
        } catch (Exception ex) {
            logger.warn("Exception trying to get the response from order service.", ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(String.format(RESPONSE_STRING_FORMAT, HOSTNAME, ex.getMessage()));
        }

    }

}
