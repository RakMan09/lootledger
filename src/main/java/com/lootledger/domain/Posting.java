package com.lootledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One leg of a transfer: a signed amount applied to an account (negative debit, positive credit). */
@Entity
@Table(name = "posting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false)
    private Long transferId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String asset;

    @Column(nullable = false)
    private long amount;
}
