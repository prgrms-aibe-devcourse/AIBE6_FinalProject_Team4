package com.kiwobollae.api.commerce.repository;

import com.kiwobollae.api.commerce.entity.UserCard;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCardRepository extends JpaRepository<UserCard, Long> {

	List<UserCard> findAllByUser_IdAndCard_IdIn(Long userId, Collection<Long> cardIds);

	Optional<UserCard> findByUser_IdAndCard_Id(Long userId, Long cardId);
}
