package com.dovimu

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform