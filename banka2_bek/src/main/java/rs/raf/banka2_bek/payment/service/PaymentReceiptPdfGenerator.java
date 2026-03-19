package rs.raf.banka2_bek.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class PaymentReceiptPdfGenerator {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final PDType1Font TITLE_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font BODY_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public byte[] generate(TransactionResponseDto transaction) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                List<String> lines = buildLines(transaction);

                contentStream.beginText();
                contentStream.setFont(TITLE_FONT, 14);
                contentStream.newLineAtOffset(50, 780);
                contentStream.showText("Transaction Receipt");
                contentStream.newLineAtOffset(0, -24);

                contentStream.setFont(BODY_FONT, 11);
                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -16);
                }
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            log.error("Failed to generate PDF receipt for transaction {}", transaction.getId(), ex);
            throw new IllegalStateException("Failed to generate transaction receipt PDF.");
        }
    }

    private List<String> buildLines(TransactionResponseDto transaction) {
        List<String> lines = new ArrayList<>();
        lines.add("Transaction ID: " + transaction.getId());
        lines.add("Date: " + (transaction.getCreatedAt() == null ? "-" : transaction.getCreatedAt().format(DATE_TIME_FORMATTER)));
        lines.add("Type: " + (transaction.getType() == null ? "-" : transaction.getType().name()));
        lines.add("Direction: " + resolveDirection(transaction));
        lines.add("From account: " + safe(transaction.getAccountNumber()));
        lines.add("To Account: " + safe(transaction.getToAccountNumber()));
        lines.add("Amount: " + resolveAmount(transaction));
        lines.add("Currency: " + safe(transaction.getCurrencyCode()));

        lines.add("Description: " + safe(transaction.getDescription()));
        return lines;
    }

    private String resolveDirection(TransactionResponseDto transaction) {
        return positive(transaction.getDebit()) ? "OUTGOING" : "INCOMING";
    }

    private String resolveAmount(TransactionResponseDto transaction) {
        BigDecimal amount = positive(transaction.getDebit()) ? transaction.getDebit() : transaction.getCredit();
        return safeDecimal(amount);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safeDecimal(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
