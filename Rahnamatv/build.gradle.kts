version = 0

cloudstream {
    authors     = listOf("cugucugu")
    language    = "tr"
    description = "İran Dizi ve Filmleri İzleme Sitesi"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf( "TvSeries" , "Movie" )
    iconUrl = "https://www.google.com/s2/favicons?domain=www.rahnama.tv&sz=%size%"
}