package com.nofrillsdb

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class NofrillsdbApplication

fun main(args: Array<String>) {
    runApplication<NofrillsdbApplication>(*args)
}
