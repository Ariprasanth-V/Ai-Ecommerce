package com.yourname.aicommerce.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourname.aicommerce.auth.Role;
import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.auth.UserRepository;
import com.yourname.aicommerce.catalog.dto.ProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Category electronics;
    private Category clothing;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        electronics = categoryRepository.save(Category.builder()
                .name("Electronics")
                .description("Gadgets & Devices")
                .build());

        clothing = categoryRepository.save(Category.builder()
                .name("Clothing")
                .description("Apparel & Fashion")
                .build());

        productRepository.save(Product.builder()
                .name("Wireless Noise Cancelling Headphones")
                .description("Premium Bluetooth over-ear headphones")
                .price(new BigDecimal("199.99"))
                .sku("AUDIO-001")
                .stockQuantity(50)
                .category(electronics)
                .active(true)
                .build());

        productRepository.save(Product.builder()
                .name("Mechanical Gaming Keyboard")
                .description("RGB Mechanical Keyboard with Tactile Switches")
                .price(new BigDecimal("89.99"))
                .sku("PERIPH-002")
                .stockQuantity(20)
                .category(electronics)
                .active(true)
                .build());

        productRepository.save(Product.builder()
                .name("Cotton T-Shirt")
                .description("100% Organic Cotton Crew Neck T-Shirt")
                .price(new BigDecimal("24.99"))
                .sku("APP-003")
                .stockQuantity(100)
                .category(clothing)
                .active(true)
                .build());

        productRepository.save(Product.builder()
                .name("Discontinued Item")
                .description("Old model product")
                .price(new BigDecimal("10.00"))
                .sku("OLD-004")
                .stockQuantity(0)
                .category(electronics)
                .active(false)
                .build());
    }

    @Test
    void getProducts_default_returnsActiveProductsOnly() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[*].sku", not(hasItem("OLD-004"))));
    }

    @Test
    void getProducts_filterByCategory_returnsMatchingProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", electronics.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("Electronics"));
    }

    @Test
    void getProducts_filterByPriceRange_returnsMatchingProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("minPrice", "50.00")
                        .param("maxPrice", "150.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("PERIPH-002"));
    }

    @Test
    void getProducts_searchKeyword_returnsMatchingProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("search", "wireless"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sku").value("AUDIO-001"));
    }

    @Test
    void getProducts_sorting_byPriceAsc() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku").value("APP-003"))
                .andExpect(jsonPath("$.data.content[1].sku").value("PERIPH-002"))
                .andExpect(jsonPath("$.data.content[2].sku").value("AUDIO-001"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteProduct_asAdmin_softDeletesProduct() throws Exception {
        Product p = productRepository.findBySku("AUDIO-001").orElseThrow();

        mockMvc.perform(delete("/api/v1/products/{id}", p.getId()))
                .andExpect(status().isOk());

        Product updated = productRepository.findById(p.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getActive()).isFalse();
    }
}
