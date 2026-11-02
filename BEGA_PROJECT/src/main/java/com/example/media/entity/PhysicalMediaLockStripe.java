package com.example.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "physical_media_lock_stripes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhysicalMediaLockStripe {
    @Id
    @Column(name = "stripe_id", nullable = false)
    private Integer id;
}
