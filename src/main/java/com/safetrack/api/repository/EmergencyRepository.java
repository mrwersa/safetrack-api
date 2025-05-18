package com.safetrack.api.repository;

import com.safetrack.api.model.Emergency;
import com.safetrack.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmergencyRepository extends JpaRepository<Emergency, Long> {
    
    @Query("SELECT e FROM Emergency e WHERE e.user = :user AND e.active = true")
    Optional<Emergency> findActiveEmergencyByUser(User user);
} 