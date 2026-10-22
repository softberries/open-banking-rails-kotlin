package com.softberries.openbanking.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * `ActionController::ParameterMissing` (`params.require(:key)` / `params.expect`, C10): the nested form key is absent or not a hash.
 * HTML routes answer 400 with the static `public/400.html` page.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
class ParameterMissingException(
    val key: String,
) : RuntimeException("param is missing or the value is empty or invalid: $key")
