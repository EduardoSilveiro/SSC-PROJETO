package utils;

public enum Constants {
    eduardoConst("https://tukanocosmos72287.documents.azure.com:443/",
            "6ulvzuHDBQgk09jceF0n5jtApjYKwJWY07RHYtJOelaZvdDj2niTMewPaG9Q4qe66Re0RTEe64yJACDbaKwEIg==", "scc2324",
            "sc2425cache72287.redis.cache.windows.net", "TAqze2ez3suwoBAdKd3nl1Tum4NHD2KBnAzCaK7vhzM="),
    tomasConst("https://scc232460182.documents.azure.com:443/",
            "S7xFx9UhYoBVAQ4wTEBz9W7KtRGGDiSdcrckwFKlGql3LfUHZ3CPBtrzvy3924hQbRopSU0DcV9gACDbL2FjNw==", "scc2324", null,
            null),

    deletedUser(null, null, "DeletedUser", null, null);

    private final String dbUrl;
    private final String dbKey;
    private final String dbName;

    private final String redisHostname;
    private final String redisKey;

    Constants(String dbUrl, String dbKey, String dbName, String redisHostname, String redisKey) {
        this.dbUrl = dbUrl;
        this.dbKey = dbKey;
        this.dbName = dbName;
        this.redisHostname = redisHostname;
        this.redisKey = redisKey;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbKey() {
        return dbKey;
    }

    public String getDbName() {
        return dbName;
    }

    public String getRedisHostname() {
        return redisHostname;
    }

    public String getRedisKey() {
        return redisKey;
    }
}
