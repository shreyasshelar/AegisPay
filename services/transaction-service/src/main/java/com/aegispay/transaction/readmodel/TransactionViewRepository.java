package com.aegispay.transaction.readmodel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionViewRepository extends MongoRepository<TransactionView, String> {

    Page<TransactionView> findByUserIdOrderByInitiatedAtDesc(String userId, Pageable pageable);
}
