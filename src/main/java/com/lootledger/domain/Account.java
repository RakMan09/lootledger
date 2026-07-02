package com.lootledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An account holds a balance of a single asset for a single owner. The {@code balance} column is a
 * cache; the ledger (postings) is the source of truth. The {@code version} column provides optimistic
 * locking so concurrent transfers cannot lose updates.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountKind kind;

    @Column(nullable = false)
    private long balance;

    @Version
    @Column(nullable = false)
    private long version;
}
