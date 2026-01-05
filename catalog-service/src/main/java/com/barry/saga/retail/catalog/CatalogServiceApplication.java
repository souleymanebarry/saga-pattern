package com.barry.saga.retail.catalog;

import com.barry.saga.retail.catalog.entities.ProductEntity;
import com.barry.saga.retail.catalog.repositories.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }

    // @Bean
    CommandLineRunner start(ProductRepository productRepository) {
        return args -> {

            List<ProductEntity> products = List.of(
                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("iPhone 15 Pro")
                            .brand("Apple")
                            .price(new BigDecimal("1229"))
                            .description("Smartphone haut de gamme avec puce A17 Pro et triple capteur photo.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("MacBook Air M3")
                            .brand("Apple")
                            .price(new BigDecimal("1499"))
                            .description("Ultrabook puissant et léger équipé de la puce Apple M3.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("PlayStation 5 Slim")
                            .brand("Sony")
                            .price(new BigDecimal("549"))
                            .description("Console next-gen avec SSD ultra rapide et jeux 4K HDR.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("Nintendo Switch OLED")
                            .brand("Nintendo")
                            .price(new BigDecimal("349"))
                            .description("Console hybride avec écran OLED vibrant.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("Samsung Galaxy S24")
                            .brand("Samsung")
                            .price(new BigDecimal("999"))
                            .description("Smartphone premium avec écran AMOLED et nouvelles fonctions IA.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("Dell XPS 15")
                            .brand("Dell")
                            .price(new BigDecimal("1899"))
                            .description("Laptop professionnel 15'' avec écran InfinityEdge.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("AirPods Pro 2")
                            .brand("Apple")
                            .price(new BigDecimal("299"))
                            .description("Écouteurs premium avec réduction de bruit active améliorée.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("Sony WH-1000XM5")
                            .brand("Sony")
                            .price(new BigDecimal("399"))
                            .description("Casque à réduction de bruit parmi les meilleurs du marché.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("Google Pixel 9")
                            .brand("Google")
                            .price(new BigDecimal("899"))
                            .description("Smartphone Pixel nouvelle génération avec caméra IA avancée.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("Logitech MX Master 3S")
                            .brand("Logitech")
                            .price(new BigDecimal("119"))
                            .description("Souris ergonomique haut de gamme pour productivité.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("LG 27'' 4K Monitor")
                            .brand("LG")
                            .price(new BigDecimal("379"))
                            .description("Écran 27 pouces UHD idéal pour travail et création.")
                            .createdAt(LocalDateTime.now())
                            .build(),

                    ProductEntity.builder()
                            .sku("SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                            .name("Brother HL-L2350DW Laser Printer")
                            .brand("Brother")
                            .price(new BigDecimal("129"))
                            .description("Imprimante laser compacte et économique.")
                            .createdAt(LocalDateTime.now())
                            .build()
            );

            productRepository.saveAll(products);

            System.out.println("📦 Catalog initialized with " + products.size() + " products !");
        };
    }


}
