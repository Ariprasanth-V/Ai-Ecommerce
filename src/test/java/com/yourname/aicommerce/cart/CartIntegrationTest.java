package com.yourname.aicommerce.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourname.aicommerce.auth.Role;
import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.auth.UserRepository;
import com.yourname.aicommerce.cart.dto.AddToCartRequest;
import com.yourname.aicommerce.cart.dto.UpdateCartItemRequest;
import com.yourname.aicommerce.catalog.Category;
import com.yourname.aicommerce.catalog.CategoryRepository;
import com.yourname.aicommerce.catalog.Product;
import com.yourname.aicommerce.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithSecurityContextTestExecutionListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CartIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .active(true)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("General")
                .build());

        testProduct = productRepository.save(Product.builder()
                .name("Test Laptop")
                .description("High performance laptop")
                .price(new BigDecimal("999.99"))
                .sku("LAPTOP-001")
                .stockQuantity(10)
                .category(category)
                .active(true)
                .build());
    }

    @Test
    void getCart_newUser_returnsEmptyCart() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(0));
    }

    @Test
    void addItem_validStock_addsItemToCart() throws Exception {
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(1999.98))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(1999.98));
    }

    @Test
    void addItem_existingProduct_incrementsQuantity() throws Exception {
        AddToCartRequest request1 = AddToCartRequest.builder()
                .productId(testProduct.getId())
                .quantity(3)
                .build();

        AddToCartRequest request2 = AddToCartRequest.builder()
                .productId(testProduct.getId())
                .quantity(4)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(7));
    }

    @Test
    void addItem_exceedsStock_returnsBadRequest() throws Exception {
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(testProduct.getId())
                .quantity(15) // stock is 10
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("exceeds available stock")));
    }

    @Test
    void updateItemQuantity_valid_updatesQuantity() throws Exception {
        AddToCartRequest addReq = AddToCartRequest.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated());

        Cart cart = cartRepository.findByUserId(testUser.getId()).orElseThrow();
        Long itemId = cart.getItems().get(0).getId();

        UpdateCartItemRequest updateReq = UpdateCartItemRequest.builder()
                .quantity(5)
                .build();

        mockMvc.perform(put("/api/v1/cart/items/{itemId}", itemId)
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(5));
    }

    @Test
    void removeItem_removesItemFromCart() throws Exception {
        AddToCartRequest addReq = AddToCartRequest.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated());

        Cart cart = cartRepository.findByUserId(testUser.getId()).orElseThrow();
        Long itemId = cart.getItems().get(0).getId();

        mockMvc.perform(delete("/api/v1/cart/items/{itemId}", itemId)
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void clearCart_removesAllItems() throws Exception {
        AddToCartRequest addReq = AddToCartRequest.builder()
                .productId(testProduct.getId())
                .quantity(2)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/cart")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }
}
