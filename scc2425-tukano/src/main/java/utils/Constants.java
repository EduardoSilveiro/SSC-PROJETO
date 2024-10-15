package main.java.utils;

public enum Constants {
    eduardoConst("https://scc2324db60353.documents.azure.com:443/",
            "Y2TBwQZcPSyzjtt3DerktHMEVWr0MZfrcriJ8w7gffqCcQMtAdoSyL3XHgCxU2HLmhGo6fxm3EMbACDbLpWZdg==", "scc2324",
            "scc2324cache60353.redis.cache.windows.net", "KWLleeCMwMy1AL7WngYv0JSsVKfFLdJUlAzCaPWF0cM=");


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
