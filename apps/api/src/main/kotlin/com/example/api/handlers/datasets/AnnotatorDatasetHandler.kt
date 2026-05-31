package com.example.api.handlers.datasets

import com.example.api.http.badRequest
import com.example.api.http.conflict
import com.example.api.http.forbidden
import com.example.api.http.unauthorized
import com.example.api.service.auth.AuthResult
import com.example.api.service.dataset.DatasetService
import io.javalin.http.Context

/**
 * 数据标注员可访问的数据集请求处理器。
 *
 * @property datasetService 数据集业务服务
 */
class AnnotatorDatasetHandler(private val datasetService: DatasetService) {
    /**
     * 处理 `GET /api/annotator/datasets` 请求。
     *
     * @param ctx Javalin 请求上下文
     */
    fun listOpen(ctx: Context) {
        when (val result = datasetService.listOpenDatasets()) {
            is AuthResult.Success -> ctx.json(result.value)
            is AuthResult.BadRequest -> ctx.badRequest(result.message)
            is AuthResult.Unauthorized -> ctx.unauthorized(result.message)
            is AuthResult.Forbidden -> ctx.forbidden(result.message)
            is AuthResult.Conflict -> ctx.conflict(result.message)
        }
    }
}

