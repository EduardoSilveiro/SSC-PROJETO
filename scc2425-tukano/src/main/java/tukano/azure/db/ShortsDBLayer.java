package main.java.tukano.azure.db;

import com.azure.cosmos.*;
import com.azure.storage.blob.BlobClientBuilder;
import main.java.tukano.api.Blobs;
import main.java.tukano.api.Result;
import main.java.tukano.api.User;
import main.java.tukano.impl.data.Following;
import main.java.tukano.impl.data.Likes;
import main.java.tukano.impl.rest.TukanoRestServer;
import main.java.utils.Constants;
import main.java.tukano.api.Short;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import static java.lang.String.format;
import static main.java.tukano.api.Result.*;
import static main.java.tukano.api.Result.ErrorCode.FORBIDDEN;


public class ShortsDBLayer {
    private static final String CONNECTION_URL = Constants.eduardoConst.getDbUrl();
    private static final String DB_KEY = Constants.eduardoConst.getDbKey();
    private static final String DB_NAME = Constants.eduardoConst.getDbName();
    private static UserDBLayer instance;


    public static synchronized UserDBLayer getInstance() {
        if (instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(CONNECTION_URL)
                .key(DB_KEY)
                // .directMode()
                .gatewayMode()
                // replace by .directMode() for better performance
                .consistencyLevel(ConsistencyLevel.SESSION)
                .connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true)
                .buildClient();
        instance = new UserDBLayer(client);
        return instance;

    }

    private CosmosClient client;
    private CosmosDatabase db;
    private CosmosContainer shorts;

    private CosmosContainer likes;
    private CosmosContainer following;

    public ShortsDBLayer(CosmosClient client) {
        this.client = client;
    }

    private synchronized void init() {
        if (db != null)
            return;
        db = client.getDatabase(DB_NAME);
        shorts = db.getContainer("shorts");
        likes = db.getContainer("likes");
        following = db.getContainer("following");

    }

    protected Result<User> okUser(String userId, String pwd) {
        return UserDBLayer.getInstance().getUser(userId, pwd);
    }
    private Result<Void> okUser(String userId) {
        var res = okUser(userId, "");
        if (res.error() == FORBIDDEN)
            return Result.ok();
        else
            return Result.error(res.error());
    }

    <T> Result<T> tryCatch(Supplier<T> supplierFunc) {
        try {
            init();
            return Result.ok(supplierFunc.get());
        } catch( CosmosException ce ) {
            //ce.printStackTrace();
            return Result.error(Result.ErrorCode.valueOf(String.valueOf(ce.getStatusCode())));
        } catch( Exception x ) {
            x.printStackTrace();
            return Result.error( Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    public Result<Short> createShort(String userId, String password) {
        return tryCatch(() -> {
            var shortId = format("%s+%s", userId, UUID.randomUUID());
            var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
            var shrt = new Short(shortId, userId, blobUrl);

            CosmosItemResponse<Short> response = shorts.createItem(shrt);
            return shrt.copyWithLikes_And_Token(0);
        });
    }

    public Result<Short> getShort(String shortId) {
        return tryCatch(() -> {
            Short shrt = shorts.readItem(shortId, new PartitionKey(shortId), Short.class).getItem();

            String query = format("SELECT COUNT(1) as likeCount FROM Likes l WHERE l.shortId = '%s'", shortId);
            CosmosPagedIterable<Long> likesResult = likes.queryItems(query, new CosmosQueryRequestOptions(),Long.class);
            long likesCount = likesResult.iterator().hasNext() ? likesResult.iterator().next() : 0;

            return shrt.copyWithLikes_And_Token(likesCount);
        });
    }

    /**
    public Result<Void> deleteShort(String shortId, String password) {
        return tryCatch(() -> {
            Result<Short> shortResult = getShort(shortId);
            return errorOrResult(shortResult, shrt -> {
                return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
                    shorts.deleteItem(shortId,new PartitionKey(shortId),new CosmosItemRequestOptions());


                    String deleteLikesQuery = format("DELETE FROM Likes WHERE shortId = '%s'", shortId);
                    likes.queryItems(deleteLikesQuery,new CosmosQueryRequestOptions(), Likes.class);

                    BlobClientBuilder blobClientBuilder = new BlobClientBuilder().endpoint(BLOB_CONTAINER_URL).blobName(shortId);
                    blobClientBuilder.buildClient().delete();

                    return null;
                });
            });
        });
    }
     **/

    public Result<List<String>> followers(String userId, String password) {
        return tryCatch(() -> {
            String query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
            CosmosPagedIterable<String> followers = following.queryItems(query,new CosmosQueryRequestOptions(),String.class);
            return followers.stream().toList();
        });
    }

    public Result<List<String>> likes(String shortId, String password) {
        return tryCatch(() -> {
            Result<Short> shortResult = getShort(shortId);
                String query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);
                CosmosPagedIterable<String> likesList = likes.queryItems(query,new CosmosQueryRequestOptions(), String.class);
                return likesList.stream().toList();
            });
    }


    public Result<List<String>> getFeed(String userId, String password) {
        return tryCatch(() -> {
            final String QUERY_FMT = """
                SELECT s.shortId, s.timestamp FROM Shorts s WHERE s.ownerId = '%s'
                UNION
                SELECT s.shortId, s.timestamp FROM Shorts s, Following f
                WHERE f.followee = s.ownerId AND f.follower = '%s'
                ORDER BY s.timestamp DESC
            """;
            String query = format(QUERY_FMT, userId, userId);
            CosmosPagedIterable<String> feed = shorts.queryItems(query,new CosmosQueryRequestOptions(),String.class);
        return feed.stream().toList();
        });
    }

    /**
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        return tryCatch(() -> {
            var following = new Following(userId1, userId2);
            return errorOrVoid(okUser(userId1, password), () -> {
                if (isFollowing) {
                    following.createItem(following);
                } else {
                    following.deleteItem(following.getId(), new PartitionKey(following.getUserId1()));
                }
                return null;
            });
        });
    }
     **/

}
