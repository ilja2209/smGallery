package com.example.smgallery

open class CommonErrorConverter : CodeErrorConverter {
    //Page 9 of Synology API documentation
    private val codes = mapOf(
        100 to "Unknown error",
        101 to "No parameter of API, method or version",
        //TODO: Fill all codes
        400 to "Invalid parameter of file operation",
        401 to "Unknown error of file operation",

        408 to "No such file or directory"
    )

    override fun convert(code: Int?): String {
        return codes[code].orEmpty()
    }
}