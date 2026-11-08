package com.example.project_flow.repository;

import com.example.project_flow.entity.AttendanceRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByWorkDate(LocalDate workDate);

    boolean existsByWorkDate(LocalDate workDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AttendanceRecord r where r.workDate = :workDate")
    Optional<AttendanceRecord> findByWorkDateForUpdate(@Param("workDate") LocalDate workDate);

    @Query("select r from AttendanceRecord r where r.checkOutAt is null order by r.workDate desc")
    List<AttendanceRecord> findOpen();

    /** Row lock so that two rapid clicks cannot both pass the state check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AttendanceRecord r where r.checkOutAt is null order by r.workDate desc")
    List<AttendanceRecord> findOpenForUpdate();

    @Query("""
            select distinct r from AttendanceRecord r left join fetch r.breaks
            where r.workDate between :from and :to order by r.workDate desc
            """)
    List<AttendanceRecord> findHistory(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
