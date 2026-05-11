package com.aegispay.notification.repository;

import com.aegispay.notification.domain.document.UserContactDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserContactRepository extends MongoRepository<UserContactDocument, String> {
}
