package org.bibletranslationtools.recorder2rc

import java.io.File


fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: <input file> <output directory>"
        )
        return
    }
    val input = File(args[0])
    val output = File(args[1])
    val result = Recorder2RCConverter().convert(input, output)
    println("\nSuccessfully converted! See output file at $result\n")
}