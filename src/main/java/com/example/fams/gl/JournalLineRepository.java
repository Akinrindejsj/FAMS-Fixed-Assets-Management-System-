package com.example.fams.gl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByAccountIdOrderByJournalEntry_DateAsc(Long accountId);

    List<JournalLine> findByJournalEntry_StatusOrderByJournalEntry_DateAsc(JournalStatus status);

    /** Lines (with entry, account and asset loaded) for entries in a date range and status set. */
    @Query("select l from JournalLine l join fetch l.journalEntry e join fetch l.account a left join fetch l.asset s "
            + "where e.date between :fromDate and :toDate and e.status in :statuses "
            + "order by e.date asc, e.id asc, l.id asc")
    List<JournalLine> findForReport(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                    @Param("statuses") Collection<JournalStatus> statuses);

    /** Every non-rejected line ever booked against one asset (for deriving ledger balances). */
    @Query("select l from JournalLine l join fetch l.journalEntry e join fetch l.account a "
            + "where l.asset.id = :assetId and e.status in :statuses")
    List<JournalLine> findByAssetAndStatuses(@Param("assetId") Long assetId,
                                             @Param("statuses") Collection<JournalStatus> statuses);

    /** Total debit/credit per account for entries in a date range and status set: [accountId, debit, credit]. */
    @Query("select a.id, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0) "
            + "from JournalLine l join l.journalEntry e join l.account a "
            + "where e.date between :fromDate and :toDate and e.status in :statuses group by a.id")
    List<Object[]> sumByAccount(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                @Param("statuses") Collection<JournalStatus> statuses);
}
