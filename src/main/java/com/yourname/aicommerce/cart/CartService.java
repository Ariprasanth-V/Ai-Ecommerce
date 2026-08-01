package com.yourname.aicommerce.cart;

import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.auth.UserRepository;
import com.yourname.aicommerce.cart.dto.AddToCartRequest;
import com.yourname.aicommerce.cart.dto.CartResponse;
import com.yourname.aicommerce.cart.dto.UpdateCartItemRequest;
import com.yourname.aicommerce.catalog.Product;
import com.yourname.aicommerce.catalog.ProductRepository;
import com.yourname.aicommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service handling shopping cart operations for authenticated users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Retrieves or creates a cart for the specified user ID.
     */
    @Transactional(readOnly = true)
    public CartResponse getCartForUser(Long userId) {
        Cart cart = getOrCreateCartEntity(userId);
        return CartResponse.fromEntity(cart);
    }

    /**
     * Adds an item to the user's cart. If the product is already in the cart,
     * increments the quantity and validates the new total quantity against stock.
     */
    public CartResponse addItemToCart(Long userId, AddToCartRequest request) {
        Cart cart = getOrCreateCartEntity(userId);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalArgumentException("Cannot add inactive product to cart");
        }

        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst();

        int targetQuantity = request.getQuantity();
        if (existingItemOpt.isPresent()) {
            targetQuantity += existingItemOpt.get().getQuantity();
        }

        if (targetQuantity > product.getStockQuantity()) {
            throw new IllegalArgumentException(String.format(
                    "Requested quantity (%d) exceeds available stock (%d) for product '%s'",
                    targetQuantity, product.getStockQuantity(), product.getName()));
        }

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            existingItem.setQuantity(targetQuantity);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cart.getItems().add(newItem);
        }

        Cart savedCart = cartRepository.save(cart);
        log.info("Added product id={} (qty={}) to cart for user id={}", product.getId(), request.getQuantity(), userId);
        return CartResponse.fromEntity(savedCart);
    }

    /**
     * Updates the quantity of a specific item in the user's cart.
     */
    public CartResponse updateItemQuantity(Long userId, Long itemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateCartEntity(userId);

        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));

        Product product = cartItem.getProduct();
        if (request.getQuantity() > product.getStockQuantity()) {
            throw new IllegalArgumentException(String.format(
                    "Requested quantity (%d) exceeds available stock (%d) for product '%s'",
                    request.getQuantity(), product.getStockQuantity(), product.getName()));
        }

        cartItem.setQuantity(request.getQuantity());
        Cart savedCart = cartRepository.save(cart);
        log.info("Updated cart item id={} quantity to {} for user id={}", itemId, request.getQuantity(), userId);
        return CartResponse.fromEntity(savedCart);
    }

    /**
     * Removes an item from the user's cart.
     */
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = getOrCreateCartEntity(userId);

        CartItem cartItem = cart.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", itemId));

        cart.getItems().remove(cartItem);
        cartItemRepository.delete(cartItem);
        Cart savedCart = cartRepository.save(cart);
        log.info("Removed cart item id={} for user id={}", itemId, userId);
        return CartResponse.fromEntity(savedCart);
    }

    /**
     * Clears all items from the user's cart.
     */
    public CartResponse clearCart(Long userId) {
        Cart cart = getOrCreateCartEntity(userId);
        cart.getItems().clear();
        Cart savedCart = cartRepository.save(cart);
        log.info("Cleared cart for user id={}", userId);
        return CartResponse.fromEntity(savedCart);
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private Cart getOrCreateCartEntity(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
                    Cart newCart = Cart.builder()
                            .user(user)
                            .build();
                    return cartRepository.save(newCart);
                });
    }
}
