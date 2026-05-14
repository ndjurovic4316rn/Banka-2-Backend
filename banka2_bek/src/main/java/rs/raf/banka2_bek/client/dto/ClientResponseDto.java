package rs.raf.banka2_bek.client.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ClientResponseDto {
    private Long id;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String phone;
    private String address;
    private Boolean active;
    /** T4A-017: ima li klijent permisiju za trgovinu hartijama (Berza + OTC). */
    private Boolean canTradeStocks;
    private LocalDateTime createdAt;
}
