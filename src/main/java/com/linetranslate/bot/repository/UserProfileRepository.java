package com.linetranslate.bot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.linetranslate.bot.model.UserProfile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends MongoRepository<UserProfile, String> {

    Optional<UserProfile> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<UserProfile> findTop10ByOrderByTotalTranslationsDesc();

    List<UserProfile> findByLastInteractionAtAfter(LocalDateTime dateTime);

    long countByLastInteractionAtBetween(LocalDateTime start, LocalDateTime end);
}