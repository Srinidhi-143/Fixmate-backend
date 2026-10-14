package com.fixmate.repository;

import com.fixmate.entity.ServiceProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceProviderRepository extends JpaRepository<ServiceProvider, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select provider from ServiceProvider provider where provider.id = :id")
    Optional<ServiceProvider> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"user", "serviceCategories"})
    Optional<ServiceProvider> findByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"user", "skills", "serviceCategories"})
    List<ServiceProvider> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = {"user", "skills", "serviceCategories"})
    List<ServiceProvider> findDistinctByServiceCategories_IdOrderByIdAsc(Long categoryId);

    @EntityGraph(attributePaths = {"user", "skills", "serviceCategories"})
    Optional<ServiceProvider> findWithProfileById(Long id);
}