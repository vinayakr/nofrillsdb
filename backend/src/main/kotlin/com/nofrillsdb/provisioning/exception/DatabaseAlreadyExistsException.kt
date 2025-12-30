package com.nofrillsdb.provisioning.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.CONFLICT)
class DatabaseAlreadyExistsException(message: String) : RuntimeException(message)