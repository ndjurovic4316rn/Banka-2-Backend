package rs.raf.banka2_bek.card.model;

/**
 * Kategorija kartice — odredjuje izvor sredstava za placanje.
 *
 * <ul>
 *   <li>{@link #DEBIT} — direktna debitacija sa povezanog Account-a (default, postojeci behavior)</li>
 *   <li>{@link #CREDIT} — kreditna kartica sa rate-ama. Banka unapred odobrava {@code creditLimit};
 *       placanja se akumuliraju u {@code outstandingBalance} i otplacuju mesecno.</li>
 *   <li>{@link #INTERNET_PREPAID} — odvojen {@code prepaidBalance} koji se top-up-uje sa Account-a.
 *       Klijent prebacuje pare unapred — odlicno za internet kupovine (limit izlozenosti).</li>
 * </ul>
 *
 * Spec C2 §266: "Vrste kartica koje indektifikujemo po MII i IIN" odnosi se na BREND (VISA/MC/DC/AMEX).
 * Ovaj enum je {@code CARDS} type-of-payment categorija — ortogonalan na {@link CardType} brend.
 */
public enum CardCategory {
    DEBIT,
    CREDIT,
    INTERNET_PREPAID
}
