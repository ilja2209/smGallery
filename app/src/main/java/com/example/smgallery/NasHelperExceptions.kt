package com.example.smgallery

import java.lang.RuntimeException

class NasHelperExceptions {
    fun fileCantBeProcessed(fileName: String): RuntimeException {
        return RuntimeException("The file $fileName can't be processed or downloaded!")
    }
}