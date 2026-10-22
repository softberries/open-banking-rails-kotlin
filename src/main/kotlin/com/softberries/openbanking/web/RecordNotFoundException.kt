package com.softberries.openbanking.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * `ActiveRecord::RecordNotFound`: a record that is missing or belongs to another tenant (C7). HTML routes answer 404 with the
 * static `public/404.html` page (30-http-api.md §A.3).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
class RecordNotFoundException(
    message: String = "Record not found",
) : RuntimeException(message)
