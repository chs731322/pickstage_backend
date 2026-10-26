package com.pickstage.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pickstage.backend.entity.UserPrivacy;

public interface UserPrivacyRepository extends JpaRepository<UserPrivacy, Long> {
}
