package com.rjhartsoftware.utilities.utils

import java.lang.RuntimeException

internal class UIActivityOnBackgroundThreadException : RuntimeException {
    constructor() : super()
    constructor(reason: String) : super(reason)
}