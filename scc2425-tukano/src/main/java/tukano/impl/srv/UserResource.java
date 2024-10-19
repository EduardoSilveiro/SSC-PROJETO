package tukano.impl.srv;

import com.azure.core.http.rest.Response;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.cookie.Cookie;
import jakarta.ws.rs.NotAuthorizedException;
import tukano.api.User;
import tukano.api.UserDAO;
import tukano.api.azure.UserDBLayer;
import utils.Hash;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response.Status;

public class UserResource {
    ObjectMapper mapper = new ObjectMapper();
    UserDBLayer userDb = UserDBLayer.getInstance();
    //static RedisCache cache = RedisCache.getInstance();

    /**
    public Response createUser(boolean isCacheActive, boolean isAuthActive, Cookie session, User user) {
        try {
            //if (isAuthActive) {
            //   checkCookieUser(session, user.getId());
            //}

            UserDAO userDAO = new UserDAO(user);
            userDAO.setPwd(Hash.of(user.getPwd()));
            CosmosItemResponse<UserDAO> u = userDb.putUser(userDAO);
            /**
            if (isCacheActive) {
                cache.setValue(userDAO.getId(), userDAO);
            }

            return Response.ok(u.getItem().toString()).build();
        } catch (

                NotAuthorizedException c) {
            return Response.status(Status.NOT_ACCEPTABLE).entity(c.getLocalizedMessage()).build();
        } catch (CosmosException c) {
            return Response.status(c.getStatusCode()).entity(c.getLocalizedMessage()).build();
        } catch (Exception e) {
            return Response.status(500).entity(e.getLocalizedMessage()).build();
        }
    }

     **/

}
