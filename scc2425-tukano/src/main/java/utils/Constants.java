package utils;

public enum Constants {
    eduardoConst("https://tukanocosmos72287.documents.azure.com:443/",
            "6ulvzuHDBQgk09jceF0n5jtApjYKwJWY07RHYtJOelaZvdDj2niTMewPaG9Q4qe66Re0RTEe64yJACDbaKwEIg==", "scc2324",
            "tukano-1728841944172.redis.cache.windows.net", "NMd5anwTl0mX4gRzgR6hfF0ytls6EztfpAzCaPUMlAs=","",false),
    tomasConst("https://scc2324204.documents.azure.com:443/",
            "RguYNsPW3CmoCZQ8vVT8uUF2d6cHycxrk7Pq3ys3ARhx77X9WYYn93vctmqKy9MRedLaJaYziLNTACDbsO7SrA==", "scc2425", null,
            null,"post",true),

    deletedUser(null, null, "DeletedUser", null, null,null,false);

    private final String dbUrl;
    private final String dbKey;
    private final String dbName;

    private final String redisHostname;
    private final String redisKey;
    private final String dbMode;

    private final boolean isCacheActive;

    Constants(String dbUrl, String dbKey, String dbName, String redisHostname, String redisKey, String dbMode, boolean isCacheActive) {
        this.dbUrl = dbUrl;
        this.dbKey = dbKey;
        this.dbName = dbName;
        this.redisHostname = redisHostname;
        this.redisKey = redisKey;
        this.dbMode = dbMode;
        this.isCacheActive = isCacheActive;
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
    public String getDbMode() {
        return dbMode;
    }

    public boolean isCacheActive(){return isCacheActive;}

}
