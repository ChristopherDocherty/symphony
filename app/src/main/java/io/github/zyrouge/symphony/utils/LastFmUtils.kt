package io.github.zyrouge.symphony.utils

fun escapeTextForLastFmUrl(text: String) : String{
    var tmp = text.replace(" ","+").replace("/","%2F")
    Logger.warn("lastfm",tmp)
    return tmp
}