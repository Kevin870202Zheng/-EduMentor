package com.edumentor.classroom.repository;

import com.edumentor.classroom.entity.SceneAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 教学动作 Repository。
 *
 * @author EduMentor Team
 */
@Repository
public interface SceneActionRepository extends JpaRepository<SceneAction, UUID> {

    /**
     * 按场景 ID 查询所有动作（按顺序排列）。
     *
     * @param sceneId 场景 ID
     * @return 动作列表
     */
    List<SceneAction> findBySceneIdOrderByOrderIndexAsc(UUID sceneId);

    /**
     * 按场景 ID 列表查询所有动作。
     *
     * @param sceneIds 场景 ID 列表
     * @return 动作列表（按场景和顺序排列）
     */
    List<SceneAction> findBySceneIdInOrderBySceneIdAscOrderIndexAsc(List<UUID> sceneIds);

    /**
     * 按场景 ID 删除所有动作。
     *
     * @param sceneId 场景 ID
     */
    void deleteBySceneId(UUID sceneId);
}
