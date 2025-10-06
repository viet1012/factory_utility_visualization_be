package com.example.factory_utility_visualization_be.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dummy_table") // Không quan trọng, chỉ để satisfy JPA
public class DummyEntity {
    @Id
    private Long id;
}
