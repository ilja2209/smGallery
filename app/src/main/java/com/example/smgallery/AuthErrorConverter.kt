package com.example.smgallery

class AuthErrorConverter : CommonErrorConverter() {
    private val codes = mapOf(
        400 to "No such account or incorrect password",
        401 to "Account disabled",
        402 to "Permission denied",
        403 to "2-step verification code required",
        404 to "Failed to authenticate 2-step verification code"
    )

    override fun convert(code: Int?): String {
        return if (code != null) {
            codes.getOrElse(code) { super.convert(code) }
        } else {
            super.convert(code)
        }
    }
}