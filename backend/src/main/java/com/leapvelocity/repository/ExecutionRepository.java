package com.leapvelocity.repository;

import com.leapvelocity.entities.Execution;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
}