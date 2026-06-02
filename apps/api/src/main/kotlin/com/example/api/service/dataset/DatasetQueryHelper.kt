package com.example.api.service.dataset

import com.example.api.db.AnnotationsTable
import com.example.api.db.DataItemsTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.util.UUID

/**
 * 数据集查询工具函数，供 ProviderDatasetService 和 AnnotatorDatasetService 复用。
 */
object DatasetQueryHelper {

    /**
     * 查询每个数据集中同时有 annotation 和 review 且 is_disputed=false 的数据项数量。
     *
     * @param datasetIds 数据集 ID 列表
     * @return 数据集 ID 到已完成数据项数量的映射
     */
    fun countFullyAnnotatedItems(datasetIds: List<UUID>): Map<UUID, Int> {
        if (datasetIds.isEmpty()) {
            return emptyMap()
        }

        val annotationRows = (AnnotationsTable innerJoin DataItemsTable)
            .select(
                DataItemsTable.datasetId,
                AnnotationsTable.itemId,
                AnnotationsTable.annotationType,
            )
            .where {
                (DataItemsTable.datasetId inList datasetIds) and
                    (AnnotationsTable.annotationType inList listOf("annotation", "review")) and
                    (AnnotationsTable.isDisputed eq false)
            }
            .toList()

        val itemTypesByDataset = mutableMapOf<UUID, MutableMap<UUID, MutableSet<String>>>()
        for (row in annotationRows) {
            val dsId = row[DataItemsTable.datasetId]
            val itemId = row[AnnotationsTable.itemId]
            val annType = row[AnnotationsTable.annotationType]
            itemTypesByDataset
                .getOrPut(dsId) { mutableMapOf() }
                .getOrPut(itemId) { mutableSetOf() }
                .add(annType)
        }

        return itemTypesByDataset.mapValues { (_, items) ->
            items.count { (_, types) -> types.size == 2 }
        }
    }
}
