package tukano.api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class UserDAO extends  User {

    @Id
    private String _rid; // Cosmos generated unique id of item
    private String _ts; // timestamp of the last update to the item


    public UserDAO() {
        super();
    }

    public UserDAO(String userId, String pwd, String email, String displayName) {
        super(userId, pwd, email, displayName);
    }

    public UserDAO(User user) {
        super(user.userId(), user.pwd(), user.email(), user.displayName());
    }

    public String get_rid() {
        return _rid;
    }

    public void set_rid(String _rid) {
        this._rid = _rid;
    }

    public String get_ts() {
        return _ts;
    }

    public void set_ts(String _ts) {
        this._ts = _ts;
    }






}
