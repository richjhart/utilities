package com.rjhartsoftware.utilities.utils

internal class BackgroundActivityOnMainThreadException : RuntimeException {
    constructor() : super()
    constructor(reason: String) : super(reason)
}