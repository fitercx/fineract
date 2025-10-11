package com.crediblex.fineract.portfolio.loc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_line_of_credit_note")
@Getter
@Setter
@NoArgsConstructor
public class LineOfCreditNote extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_of_credit_id", nullable = false)
    private LineOfCredit lineOfCredit;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "note_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private LineOfCreditNoteType noteType;

    public LineOfCreditNote(LineOfCredit lineOfCredit, String note, LineOfCreditNoteType noteType) {
        this.lineOfCredit = lineOfCredit;
        this.note = note;
        this.noteType = noteType;
    }
}
