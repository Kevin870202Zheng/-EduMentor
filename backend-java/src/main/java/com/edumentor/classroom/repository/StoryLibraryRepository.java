package com.edumentor.classroom.repository;

import com.edumentor.classroom.entity.StoryLibrary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StoryLibraryRepository extends JpaRepository<StoryLibrary, UUID> {

    List<StoryLibrary> findByStatusOrderByCreatedAtDesc(String status);

    List<StoryLibrary> findByStatusAndThemeIdOrderByCreatedAtDesc(String status, UUID themeId);
}
