package com.example.api.http

import com.example.api.models.ErrorResponse
import io.javalin.http.Context

fun Context.error(statusCode: Int, message: String) {
    status(statusCode).json(ErrorResponse(message))
}

fun Context.badRequest(message: String) {
    error(400, message)
}

fun Context.unauthorized(message: String) {
    error(401, message)
}

fun Context.forbidden(message: String) {
    error(403, message)
}

fun Context.conflict(message: String) {
    error(409, message)
}

fun Context.internalServerError(message: String) {
    error(500, message)
}
