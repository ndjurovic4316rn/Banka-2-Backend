package rs.raf.banka2_bek.client.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.card.model.Card;
import rs.raf.banka2_bek.payment.model.PaymentRecipient;

@Entity
@Table(name = "clients", uniqueConstraints = {
        @UniqueConstraint(name = "uk_clients_email", columnNames = "email")
}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String firstName;

    @Column(nullable = false, length = 50)
    private String lastName;

    @Column
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender;          // M / F / OTHER

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String address;

    @Column(nullable = false, length = 255)
    private String password;        // Heširana lozinka (bcrypt/Argon2)

    @Column(nullable = false, length = 64)
    private String saltPassword;    // Random salt, generiše se pri kreiranju

    // Token za aktivaciju naloga — brisan nakon aktivacije
    @Column(length = 255)
    private String activationToken;

    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private Boolean active = false;

    // T4A-017 fix (spec C3 §6, C4 §31): klijenti su podeljeni na "moze da traduje"
    // i "ne moze". Po default true radi backwards-compatibility — supervizor moze
    // pojedinacno da revokuje preko `PATCH /clients/{id}/trading`.
    @Column(name = "can_trade_stocks", nullable = false)
    @org.hibernate.annotations.ColumnDefault("1")
    @Builder.Default
    private Boolean canTradeStocks = true;

    @Column(nullable = false, updatable = false)
    @org.hibernate.annotations.ColumnDefault("CURRENT_TIMESTAMP")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Relacije ──────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Account> accounts = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PaymentRecipient> paymentRecipients = new ArrayList<>();

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Card> cards = new ArrayList<>();
}
