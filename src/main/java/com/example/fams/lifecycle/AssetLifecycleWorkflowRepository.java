package com.example.fams.lifecycle;

import com.example.fams.assets.Asset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetLifecycleWorkflowRepository extends JpaRepository<AssetLifecycleWorkflow, Long> {

    @EntityGraph(attributePaths = "asset")
    List<AssetLifecycleWorkflow> findByAssetOrderByRequestedAtDesc(Asset asset);

    @EntityGraph(attributePaths = "asset")
    List<AssetLifecycleWorkflow> findByAssetInOrderByRequestedAtDesc(List<Asset> assets);

    @EntityGraph(attributePaths = "asset")
    List<AssetLifecycleWorkflow> findAllByOrderByRequestedAtDesc();

    long countByAssetInAndStatus(List<Asset> assets, LifecycleWorkflowStatus status);

    Optional<AssetLifecycleWorkflow> findByProcessInstanceId(String processInstanceId);
}
