package rs.raf.banka2_bek.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class FundsVerificationService {

    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;

    public void verify(CreateOrderDto dto, Long userId, BigDecimal approximatePrice, Listing listing, OrderType orderType, OrderDirection direction) {
        Account account = accountRepository.findById(dto.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (dto.isMargin()) {
            verifyMargin(account, listing);
        }

        if (direction == OrderDirection.BUY) {
            verifyBuy(account, approximatePrice, orderType);
        } else {
            verifySell(userId, listing, dto.getQuantity());
        }
    }

    private void verifyMargin(Account account, Listing listing) {
        BigDecimal maintenanceMargin = computeMaintenanceMargin(listing);
        BigDecimal initialMarginCost = maintenanceMargin.multiply(new BigDecimal("1.1")).setScale(4, RoundingMode.HALF_UP);

        boolean balanceSufficient = account.getBalance().compareTo(initialMarginCost) > 0;
        boolean creditSufficient = account.getAvailableBalance().compareTo(initialMarginCost) > 0;

        if (!balanceSufficient && !creditSufficient) {
            throw new IllegalArgumentException("Insufficient funds for margin order");
        }
    }

    private BigDecimal computeMaintenanceMargin(Listing listing) {
        if (listing.getListingType() == ListingType.STOCK) {
            return listing.getPrice().multiply(new BigDecimal("0.5"));
        } else {
            // FOREX and FUTURES: contractSize * price * 10%
            int contractSize = listing.getContractSize() != null ? listing.getContractSize() : 1;
            return BigDecimal.valueOf(contractSize)
                    .multiply(listing.getPrice())
                    .multiply(new BigDecimal("0.1"))
                    .setScale(4, RoundingMode.HALF_UP);
        }
    }

    private void verifyBuy(Account account, BigDecimal approximatePrice, OrderType orderType) {
        BigDecimal commission = computeCommission(approximatePrice, orderType);
        BigDecimal required = approximatePrice.add(commission);

        if (account.getBalance().compareTo(required) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
    }

    private BigDecimal computeCommission(BigDecimal approximatePrice, OrderType orderType) {
        // Spec: Market → min(14% * price, $7), Limit → min(24% * price, $12)
        // "u zavisnosti od toga koji iznos je manji"
        return switch (orderType) {
            case MARKET, STOP -> approximatePrice.multiply(new BigDecimal("0.14")).min(new BigDecimal("7"));
            case LIMIT, STOP_LIMIT -> approximatePrice.multiply(new BigDecimal("0.24")).min(new BigDecimal("12"));
        };
    }

    private void verifySell(Long userId, Listing listing, int quantity) {
        // Check actual portfolio holdings (not just completed orders)
        int currentHolding = portfolioRepository.findByUserId(userId).stream()
                .filter(p -> p.getListingId().equals(listing.getId()))
                .mapToInt(Portfolio::getQuantity)
                .sum();

        if (currentHolding < quantity) {
            throw new IllegalArgumentException("Insufficient securities in portfolio. You have " + currentHolding + " but tried to sell " + quantity);
        }
    }
}
