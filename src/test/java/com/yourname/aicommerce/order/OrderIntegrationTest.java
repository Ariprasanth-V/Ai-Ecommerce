package com.yourname.aicommerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourname.aicommerce.auth.Role;
import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.auth.UserRepository;
import com.yourname.aicommerce.cart.Cart;
import com.yourname.aicommerce.cart.CartItem;
import com.yourname.aicommerce.cart.CartRepository;
import com.yourname.aicommerce.catalog.Category;
import com.yourname.aicommerce.catalog.CategoryRepository;
import com.yourname.aicommerce.catalog.Product;
import com.yourname.aicommerce.catalog.ProductRepository;
import com.yourname.aicommerce.order.dto.CreateOrderRequest;
import com.yourname.aicommerce.order.dto.UpdateOrderStatusRequest;
import com.yourname.aicommerce.order.event.OrderPlacedEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"order-events"})
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, OrderPlacedEvent> testConsumer;
    private User customerUser;
    private User adminUser;
    private Product product1;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // Setup EmbeddedKafka test consumer with unique group ID
        String groupId = "test-group-" + java.util.UUID.randomUUID();
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        DefaultKafkaConsumerFactory<String, OrderPlacedEvent> cf = new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new JsonDeserializer<>(OrderPlacedEvent.class, false));
        testConsumer = cf.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(testConsumer, "order-events");

        customerUser = userRepository.save(User.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .active(true)
                .build());

        adminUser = userRepository.save(User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ADMIN)
                .active(true)
                .build());

        Category category = categoryRepository.save(Category.builder().name("Tech").build());

        product1 = productRepository.save(Product.builder()
                .name("Smart Watch")
                .description("Fitness tracker watch")
                .price(new BigDecimal("199.99"))
                .sku("WATCH-001")
                .stockQuantity(10)
                .category(category)
                .active(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (testConsumer != null) {
            testConsumer.close();
        }
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createOrder_validStock_decrementsStock_clearsCart_andPublishesEventToKafkaTopic() throws Exception {
        // Setup cart with 3 items of product1 (stock is 10)
        Cart cart = Cart.builder().user(customerUser).items(new ArrayList<>()).build();
        cart.getItems().add(CartItem.builder().cart(cart).product(product1).quantity(3).build());
        cartRepository.save(cart);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingAddress(ShippingAddress.builder()
                        .street("123 Main St")
                        .city("TechCity")
                        .state("TC")
                        .zipCode("12345")
                        .country("USA")
                        .build())
                .build();

        String responseString = mockMvc.perform(post("/api/v1/orders")
                        .with(user(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalAmount").value(599.97))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        Long createdOrderId = objectMapper.readTree(responseString).get("data").get("id").asLong();

        // Verify stock decremented from 10 to 7
        Product updatedProduct = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualTo(7);

        // Verify cart is cleared
        Cart clearedCart = cartRepository.findByUserId(customerUser.getId()).orElseThrow();
        assertThat(clearedCart.getItems()).isEmpty();

        // Verify real message published to Kafka topic matching created order ID
        var records = KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(5));
        OrderPlacedEvent publishedEvent = null;
        for (ConsumerRecord<String, OrderPlacedEvent> rec : records) {
            if (rec.value() != null && createdOrderId.equals(rec.value().getOrderId())) {
                publishedEvent = rec.value();
                break;
            }
        }

        assertThat(publishedEvent).isNotNull();
        assertThat(publishedEvent.getUserId()).isEqualTo(customerUser.getId());
        assertThat(publishedEvent.getTotalAmount()).isEqualByComparingTo(new BigDecimal("599.97"));
        assertThat(publishedEvent.getItems()).hasSize(1);
        assertThat(publishedEvent.getItems().get(0).getProductId()).isEqualTo(product1.getId());
    }

    @Test
    void createOrder_insufficientStock_failsCleanly_andNoKafkaEventPublished() throws Exception {
        // Setup cart with 15 items of product1 (stock is only 10)
        Cart cart = Cart.builder().user(customerUser).items(new ArrayList<>()).build();
        cart.getItems().add(CartItem.builder().cart(cart).product(product1).quantity(15).build());
        cartRepository.save(cart);

        CreateOrderRequest request = CreateOrderRequest.builder().build();

        mockMvc.perform(post("/api/v1/orders")
                        .with(user(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient stock")));

        // Verify stock was NOT modified
        Product untouchedProduct = productRepository.findById(product1.getId()).orElseThrow();
        assertThat(untouchedProduct.getStockQuantity()).isEqualTo(10);

        // Verify no Kafka event matching customerUser's ID was published during this attempt
        var records = KafkaTestUtils.getRecords(testConsumer, Duration.ofSeconds(2));
        boolean matchingEventFound = false;
        for (ConsumerRecord<String, OrderPlacedEvent> rec : records) {
            if (rec.value() != null && customerUser.getId().equals(rec.value().getUserId())) {
                matchingEventFound = true;
                break;
            }
        }
        assertThat(matchingEventFound).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateOrderStatus_invalidTransition_returnsBadRequest() throws Exception {
        // Create an order in PENDING state
        Cart cart = Cart.builder().user(customerUser).items(new ArrayList<>()).build();
        cart.getItems().add(CartItem.builder().cart(cart).product(product1).quantity(1).build());
        cartRepository.save(cart);

        mockMvc.perform(post("/api/v1/orders")
                        .with(user(customerUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        Order order = orderRepository.findAll().get(0);

        // Attempt invalid transition PENDING -> DELIVERED (must go via CONFIRMED/PROCESSING -> SHIPPED)
        UpdateOrderStatusRequest updateReq = UpdateOrderStatusRequest.builder()
                .status(OrderStatus.DELIVERED)
                .build();

        mockMvc.perform(patch("/api/v1/orders/{id}/status", order.getId())
                        .with(user(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid order status transition")));
    }
}
