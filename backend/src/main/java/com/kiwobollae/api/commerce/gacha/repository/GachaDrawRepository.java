package com.kiwobollae.api.commerce.gacha.repository;

import com.kiwobollae.api.commerce.gacha.entity.GachaDraw;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaDrawStatus;
import com.kiwobollae.api.commerce.gacha.entity.enums.GachaSourceType;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GachaDrawRepository extends JpaRepository<GachaDraw, Long> {

  Optional<GachaDraw> findBySourceTypeAndSourceId(GachaSourceType sourceType, Long sourceId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from GachaDraw d where d.id = :id")
  Optional<GachaDraw> findByIdForUpdate(@Param("id") Long id);

  Optional<GachaDraw> findByIdAndUser_Id(Long id, Long userId);

  @EntityGraph(attributePaths = "user")
  @Query(
      """
			select d
			from GachaDraw d
			where (:status is null or d.status = :status)
			  and (:userId is null or d.user.id = :userId)
			order by d.createdAt desc
			""")
  Page<GachaDraw> findAdminHistory(
      @Param("status") GachaDrawStatus status, @Param("userId") Long userId, Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
			update GachaDraw d
			set d.resultViewedAt = :viewedAt, d.updatedAt = :viewedAt
			where d.id = :id
			  and d.user.id = :userId
			  and d.status = :completed
			  and d.resultViewedAt is null
			""")
  int markViewedIfAbsent(
      @Param("id") Long id,
      @Param("userId") Long userId,
      @Param("completed") GachaDrawStatus completed,
      @Param("viewedAt") LocalDateTime viewedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
			update GachaDraw d
			set d.status = :pending,
				d.nextRetryAt = null,
				d.updatedAt = :now
			where d.id = :id
			  and d.status = :manualReview
			""")
  int requeueManualReview(
      @Param("id") Long id,
      @Param("manualReview") GachaDrawStatus manualReview,
      @Param("pending") GachaDrawStatus pending,
      @Param("now") LocalDateTime now);

  @Query(
      """
			select d
			from GachaDraw d
			where d.user.id = :userId
			  and (:viewed is null
			       or (:viewed = true and d.resultViewedAt is not null)
			       or (:viewed = false and d.resultViewedAt is null and d.status <> :refunded))
			order by d.createdAt desc
			""")
  Page<GachaDraw> findHistory(
      @Param("userId") Long userId,
      @Param("viewed") Boolean viewed,
      @Param("refunded") GachaDrawStatus refunded,
      Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
			update GachaDraw d
			set d.status = :processing, d.updatedAt = :now
			where d.id = :id
			  and (
			    d.status = :pending
			    or (d.status = :retryable and d.nextRetryAt <= :now)
			  )
			""")
  int claimForProcessing(
      @Param("id") Long id,
      @Param("now") LocalDateTime now,
      @Param("pending") GachaDrawStatus pending,
      @Param("retryable") GachaDrawStatus retryable,
      @Param("processing") GachaDrawStatus processing);

  @Query(
      """
			select d.id
			from GachaDraw d
			where d.status = :pending
			   or (d.status = :retryable and d.nextRetryAt <= :now)
			order by d.createdAt asc
			""")
  List<Long> findProcessableIds(
      @Param("pending") GachaDrawStatus pending,
      @Param("retryable") GachaDrawStatus retryable,
      @Param("now") LocalDateTime now,
      Pageable pageable);

  @Query(
      """
			select d.id
			from GachaDraw d
			where d.status = :processing and d.updatedAt < :staleBefore
			order by d.updatedAt asc
			""")
  List<Long> findStaleProcessingIds(
      @Param("processing") GachaDrawStatus processing,
      @Param("staleBefore") LocalDateTime staleBefore,
      Pageable pageable);
}
