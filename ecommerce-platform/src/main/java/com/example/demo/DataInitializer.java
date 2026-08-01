package com.example.demo;

import com.example.demo.entity.Category;
import com.example.demo.entity.Product;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            log.info("Database already seeded with categories. Skipping initialization.");
            return;
        }

        log.info("Starting data initialization for V0ID Brand...");

        // 1. Seed Categories
        Category apparel = Category.builder().name("Apparel").description("Future-proof urban wear").build();
        Category footwear = Category.builder().name("Footwear").description("Engineered for the urban explorer").build();
        Category accessories = Category.builder().name("Accessories").description("Tactical everyday carry").build();

        List<Category> savedCategories = categoryRepository.saveAll(Arrays.asList(apparel, footwear, accessories));

        // Map categories for easy lookup by name
        Map<String, Category> categoryMap = savedCategories.stream()
                .collect(Collectors.toMap(Category::getName, c -> c));

        // 2. Seed Products
        List<Product> products = Arrays.asList(
                // --- APPAREL ---
                createProduct("Heavyweight 'V0ID' Tee", "6.5oz pre-shrunk cotton with a boxy, oversized fit and high-density screen print.", 45.00, 42, "https://images.unsplash.com/photo-1576566588028-4147f3842f27?auto=format&fit=crop&w=800&q=80", categoryMap.get("Apparel")),
                createProduct("Cyber-Shell Windbreaker", "Water-resistant ripstop nylon with reflective accents and modular utility pockets.", 125.00, 12, "https://images.unsplash.com/photo-1551488831-00ddcb6c6bd3?auto=format&fit=crop&w=800&q=80", categoryMap.get("Apparel")),
                createProduct("Tactical Cargo Joggers", "Articulated knees and adjustable ankle straps for a customizable tech-wear silhouette.", 95.00, 25, "https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?auto=format&fit=crop&w=800&q=80", categoryMap.get("Apparel")),
                createProduct("Boxy Logo Hoodie", "450GSM French Terry. Drop-shoulder design with no drawstrings for a clean look.", 85.00, 30, "https://images.unsplash.com/photo-1556821840-3a63f95609a7?auto=format&fit=crop&w=800&q=80", categoryMap.get("Apparel")),

                // --- FOOTWEAR ---
                createProduct("Phantom Runner V1", "Lightweight mesh upper with carbon-fiber plate for maximum energy return.", 160.00, 8, "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=800&q=80", categoryMap.get("Footwear")),
                createProduct("Urban Combat Boot", "Full-grain leather and ballistic nylon hybrid with a Vibram® lugged sole.", 210.00, 15, "https://images.unsplash.com/photo-1608256246200-53e635b5b65f?auto=format&fit=crop&w=800&q=80", categoryMap.get("Footwear")),
                createProduct("Neon-Grid Sneakers", "Translucent panels and neon stitching. Includes dual-set reflective laces.", 135.00, 0, "https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?auto=format&fit=crop&w=800&q=80", categoryMap.get("Footwear")),
                createProduct("Minimalist Low-Top", "Clean white Italian leather sneakers with a hidden lacing system.", 120.00, 18, "https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?auto=format&fit=crop&w=800&q=80", categoryMap.get("Footwear")),

                // --- ACCESSORIES ---
                createProduct("Modular Sling Bag", "Weatherproof X-Pac fabric with magnetic FIDLOCK buckles and internal organizers.", 65.00, 50, "https://images.unsplash.com/photo-1622560480605-d83c853bc5c3?auto=format&fit=crop&w=800&q=80", categoryMap.get("Accessories")),
                createProduct("Carbon Fiber Wallet", "RFID-blocking slim profile. Holds up to 12 cards and cash via spring clip.", 45.00, 85, "https://images.unsplash.com/photo-1627123424574-724758594e93?auto=format&fit=crop&w=800&q=80", categoryMap.get("Accessories")),
                createProduct("Matte Black Carabiner", "Aerospace-grade aluminum with a quick-release gate. Not for climbing.", 18.00, 100, "https://images.unsplash.com/photo-1547481795-4f7ad07e2871?auto=format&fit=crop&w=800&q=80", categoryMap.get("Accessories")),
                createProduct("Steel Water Flask", "32oz vacuum-insulated stainless steel. Keeps liquids cold for 24 hours.", 35.00, 40, "https://images.unsplash.com/photo-1602143399827-bd95ef6f0729?auto=format&fit=crop&w=800&q=80", categoryMap.get("Accessories"))
        );

        productRepository.saveAll(products);
        log.info("Successfully seeded database with {} products.", products.size());
    }

    private Product createProduct(String name, String desc, double price, int stock, String img, Category cat) {
        return Product.builder()
                .name(name)
                .description(desc)
                .price(BigDecimal.valueOf(price))
                .stockQuantity(stock)
                .imageUrl(img)
                .category(cat)
                .build();
    }
}