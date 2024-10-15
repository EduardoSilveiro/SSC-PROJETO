package main.java.tukano.azure.db;

import com.azure.cosmos.util.CosmosPagedIterable;
import main.java.tukano.api.Result;
import main.java.tukano.api.User;
import main.java.utils.Constants;
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

import main.java.tukano.api.Result.*;

import java.util.List;
import java.util.function.Supplier;

import static main.java.tukano.api.Result.*;
import static main.java.tukano.api.Result.ErrorCode.BAD_REQUEST;
import static main.java.tukano.api.Result.ErrorCode.FORBIDDEN;

public class UserDBLayer {
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
    private CosmosContainer users;

    public UserDBLayer(CosmosClient client) {
        this.client = client;
    }

    private synchronized void init() {
        if (db != null)
            return;
        db = client.getDatabase(DB_NAME);
        users = db.getContainer("users");

    }

    <T> Result<T> tryCatch( Supplier<T> supplierFunc) {
        try {
            init();
            return Result.ok(supplierFunc.get());
        } catch( CosmosException ce ) {
            //ce.printStackTrace();
            return Result.error(ErrorCode.valueOf(String.valueOf(ce.getStatusCode())));
        } catch( Exception x ) {
            x.printStackTrace();
            return Result.error( ErrorCode.INTERNAL_ERROR);
        }
    }

    private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
        if( res.isOK())
            return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
        else
            return res;
    }

    public Result<String> createUser(User user) {
        return tryCatch(() -> {
            CosmosItemResponse<User> response = users.createItem(user);
            return user.getUserId();
        });
    }

    public Result<User> getUser(String userId, String pwd) {
        return tryCatch(() -> {
            User user = users.readItem(userId, new PartitionKey(userId), User.class).getItem();
            return validatedUserOrError(Result.ok(user), pwd).value();
        });
    }

    public Result<User> updateUser(String userId, String pwd, User other){
        return tryCatch(() -> {
            User existingUser = users.readItem(userId, new PartitionKey(userId), User.class).getItem();
            if (validatedUserOrError(Result.ok(existingUser), pwd).isOK()) {
                existingUser.updateFrom(other);
                users.replaceItem(existingUser, userId, new PartitionKey(userId),new CosmosItemRequestOptions());
                return existingUser;
            }
            return null;
        });
    }

    public Result<User> deleteUser(String userId, String pwd) {
        return tryCatch(() -> {
            User user = users.readItem(userId, new PartitionKey(userId), User.class).getItem();
            if (validatedUserOrError(Result.ok(user), pwd).isOK()) {
                users.deleteItem(user, new CosmosItemRequestOptions());
                return user;
            } return null;
        });
    }

    public Result<List<User>> searchUsers(String pattern) {
        return tryCatch(() -> {
            String query = String.format("SELECT * FROM Users u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());
            CosmosPagedIterable<User> usersIterable = users.queryItems(query,new CosmosQueryRequestOptions(), User.class);

            List<User> hits = usersIterable.stream()
                    .map(User::copyWithoutPassword)
                    .toList();
            return hits;
        });
    }

    public CosmosItemResponse<Object> delUserById(String id) {
        init();
        PartitionKey key = new PartitionKey(id);
        return users.deleteItem(id, key, new CosmosItemRequestOptions());
    }


}
