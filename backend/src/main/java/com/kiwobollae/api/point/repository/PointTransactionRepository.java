package com.kiwobollae.api.point.repository;

import com.kiwobollae.api.point.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
}
