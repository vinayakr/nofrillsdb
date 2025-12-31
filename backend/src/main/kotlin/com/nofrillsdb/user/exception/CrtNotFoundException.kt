package com.nofrillsdb.user.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.NOT_FOUND)
class CrtNotFoundException(message: String) : RuntimeException(message)